package edu.ncsu.dlf.servlet;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


import org.eclipse.egit.github.core.Issue;
import org.eclipse.egit.github.core.client.GitHubClient;
import org.eclipse.egit.github.core.service.IssueService;
import org.eclipse.egit.github.core.service.UserService;
import org.eclipse.egit.github.core.service.ContentsService;
import org.eclipse.egit.github.core.service.RepositoryService;
import org.eclipse.egit.github.core.Repository;
import org.eclipse.egit.github.core.RepositoryContents;

import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.utils.URIBuilder;
import java.io.ByteArrayOutputStream;
import org.apache.http.entity.StringEntity;
import org.json.JSONObject;
import org.apache.http.impl.client.HttpClients;
import java.net.URI;
import java.net.URISyntaxException;

import org.apache.commons.fileupload.FileItemIterator;
import org.apache.commons.fileupload.FileItemStream;
import org.apache.commons.fileupload.servlet.ServletFileUpload;

import javax.xml.bind.DatatypeConverter;

import org.eclipse.egit.github.core.User;

import java.io.InputStream;

import edu.ncsu.dlf.model.Pdf;
import edu.ncsu.dlf.model.Repo;
import edu.ncsu.dlf.model.PdfComment;

public class FileUploadServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;
	public static final String pathToCommentBoxImage = "/images/comment_box.PNG";

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		InputStream fileStream = getFileInputSteamFromReq(req);
		InputStream commentBoxImageStream = getServletContext().getResourceAsStream(pathToCommentBoxImage);

		//Instantiate GitHub Client
		String accessToken = req.getParameter("access_token");
		GitHubClient client = new GitHubClient();
		client = client.setOAuth2Token(accessToken);

		//Instantiate the Repo object
		String activeUser = getUsernameOfLoggedInUser(accessToken);
		String selectedRepository = req.getParameter("selectedRepository");
		Repo repo = new Repo(activeUser, selectedRepository);

		int totalIssues = getNumTotalIssues(client, repo);

		Pdf test = new Pdf(fileStream, commentBoxImageStream);
		//List<PdfComment> comments = test.getPDFComments();

		//What exactly is this doing?
		List<PdfComment> comments = updatePdfWithNumberedAndColoredAnnotations(test, repo, totalIssues);

		String selectedBranch = req.getParameter("selectedBranch");
		String urlToPDFInRepo = addPdfToRepo(test, activeUser, selectedBranch, client, repo, accessToken);

		test.close(); //Close the PDF
		UploadIssuesRunnable task = new UploadIssuesRunnable();
		//What are custom labels?
		List<String> customLabels = new ArrayList<String>();
		task.setter(comments, accessToken, repo, totalIssues, customLabels);

		Thread t = new Thread(task);
		t.start();

		//Don't return control to front end, until all issues are created.
		while(task.getCommentsToIssues() < comments.size()) {}

		int finalIssues = getNumTotalIssues(client, repo);
		String successMessage = (finalIssues - totalIssues) + " issues have been created!";

		String fullPDFUrl = String.format(
			"https://github.com/%s/%s/tree/%s/%s", 
			activeUser, selectedRepository, selectedBranch, urlToPDFInRepo
		);
		successMessage += "\n\n" + "The PDF file has been archived to : " + fullPDFUrl;

		resp.getWriter().write(successMessage);

		System.out.println(successMessage);

	}

	private InputStream getFileInputSteamFromReq(HttpServletRequest req) throws IOException {
		try {
			final ServletFileUpload upload = new ServletFileUpload();
			FileItemIterator iter = upload.getItemIterator(req);
			FileItemStream file = iter.next();
			return file.openStream();
		} catch(Exception e) {
			e.printStackTrace();
			throw new IOException("Unable to process file");
		}
	}

	private String getUsernameOfLoggedInUser(String accessToken) throws IOException {
		UserService userService = new UserService();
		userService.getClient().setOAuth2Token(accessToken);
		User u = userService.getUser();

		return u.getLogin();
	}

	private int getNumTotalIssues(GitHubClient client, Repo repo) throws IOException {
	    IssueService issueService = new IssueService(client);

	    Map<String, String> prefs = new HashMap<String, String>();
	    //By default, only open issues are shown
	    prefs.put(IssueService.FILTER_STATE, "all");
	    //get all issues for this repo
	    List<Issue> issues = issueService.getIssues(repo.repoOwner, repo.repoName, prefs);

        return issues.size();
	}
	
	private List<PdfComment> updatePdfWithNumberedAndColoredAnnotations(Pdf pdf, Repo repo, int totalIssues) throws IOException {
	    List<PdfComment> pdfComments = pdf.getPDFComments();
		if(!pdfComments.isEmpty()) {
			// Set the issue numbers
			int issueNumber = totalIssues + 1;
			for(PdfComment com : pdfComments) {
				if(com.getIssueNumber() == 0) {
					com.setIssueNumber(issueNumber++);
				}
			}

			// Update the comments to link to the repository and their newly assigned issue number
			pdf.updateCommentsWithColorsAndLinks(pdfComments, repo);
		}
		return pdfComments;
	}

	private String addPdfToRepo(Pdf pdf, String activeUser, String selectedBranch, GitHubClient client, Repo repo, String accessToken) throws IOException {
		String fileName = activeUser + "-" + java.time.LocalDate.now().toString() + ".pdf";
		String filePath = "reviews/" + fileName;
        String sha = null;
        ContentsService contents = new ContentsService(client);
        try {
            //list all the files in reviews.  We can't just fetch our paper, because it might be
            //bigger than 1MB which breaks this API call
            List<RepositoryContents> files = contents.getContents(getRepo(client, repo), "reviews/");
            for(RepositoryContents file: files) {
                if (file.getName().equals(fileName)) {
                    sha = file.getSha();
                }
            }
        } catch(IOException e) {
			System.out.println("No exisiting files in the reviews folder!\n");
            //e.printStackTrace();
        }

        HttpPut request = new HttpPut(buildURIForFileUpload(accessToken, repo.repoOwner, repo.repoName, filePath));
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            pdf.getDocument().save(output);

            String content = DatatypeConverter.printBase64Binary(output.toByteArray());

            JSONObject json = new JSONObject();
            if (sha == null) {
                //if we are uploading the review for the first time
                json.put("message", "Archiving review of PDF: " + fileName);
            } else {
                //updating review
                json.put("message", "Archiving review of PDF: " + fileName);
                json.put("sha", sha);
            }

            json.put("path", filePath);
			json.put("content", content);
			json.put("branch", selectedBranch);

            StringEntity entity = new StringEntity(json.toString());
            entity.setContentType("application/json");
            request.setEntity(entity);

            HttpClients.createDefault().execute(request);
        } catch(Exception e) {
            e.printStackTrace();
        } finally {
            request.releaseConnection();
        }

        return filePath;
	}
	
	private Repository getRepo(GitHubClient client, Repo repo) throws IOException {
        RepositoryService repoService = new RepositoryService(client);
        return repoService.getRepository(repo.repoOwner, repo.repoName);
	}
	
	private URI buildURIForFileUpload(String accessToken, String writerLogin, String repoName, String filePath) throws IOException {
        try {
            URIBuilder builder = new URIBuilder("https://api.github.com/repos/" + writerLogin + '/' + repoName + "/contents/" + filePath);
            builder.addParameter("access_token", accessToken);

            return builder.build();
        } catch (URISyntaxException e) {
            throw new IOException("Could not build uri", e);
        }

	}
	
}
