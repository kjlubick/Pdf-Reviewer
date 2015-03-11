package edu.ncsu.dlf.servlet;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import edu.ncsu.dlf.HttpUtils;
import edu.ncsu.dlf.database.DBAbstraction;
import edu.ncsu.dlf.database.DatabaseFactory;
import edu.ncsu.dlf.model.Review;

import org.eclipse.egit.github.core.Issue;
import org.eclipse.egit.github.core.Label;
import org.eclipse.egit.github.core.Repository;
import org.eclipse.egit.github.core.User;
import org.eclipse.egit.github.core.client.GitHubClient;
import org.eclipse.egit.github.core.service.CollaboratorService;
import org.eclipse.egit.github.core.service.IssueService;
import org.eclipse.egit.github.core.service.LabelService;
import org.eclipse.egit.github.core.service.RepositoryService;
import org.eclipse.egit.github.core.service.UserService;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class ReviewRequestServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;
    private String repoName;
    private String pathToPaper;
    private String paper;
    private String login;
    private transient GitHubClient client;
    private transient UserService userService;
    private transient IssueService issueService;
    private Repository repo;
    private transient CollaboratorService collaboratorService;
    private User reviewRequester;
    private String access_token;
    private String repoOwner;

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		try {
			String body = HttpUtils.getRequestBody(req);
			access_token = req.getParameter("access_token");
			List<User> reviewers = parseRequest(body);	

			boolean isOrg = "Organization".equals(reviewRequester.getType());

			addCommentsToPDF(pathToPaper, paper);

			for(User reviewer : reviewers) {
				if(!isOrg) {
				    try {
				        collaboratorService.addCollaborator(repo, reviewer.getLogin());
                    } catch (Exception e) {
                        //TODO warn user
                        System.out.println("Could not add collaborator, maybe you don't own the repo?");
                    }
				}
				try {
					String downloadLink = makeReviewRequestLabel(reviewer);
					DBAbstraction database = DatabaseFactory.getDatabase();
		        
					Review newReview = new Review(reviewer.getLogin(), repoOwner, userService.getUser().getLogin(), repoName, paper, downloadLink, new UserService(client));
					
					database.addReviewToDatastore(newReview);
				} catch(IOException e) {
					resp.setStatus(417);
				}
			}
		} catch (JSONException e) {
			e.printStackTrace();
		}
	}

    private String makeReviewRequestLabel(User reviewer) throws IOException {
        Label reviewRequestLabel = new Label().setColor("009800").setName("Review Request");
        
        try {
        	LabelService labelService = new LabelService(client);
        	labelService.createLabel(repoOwner, repoName, reviewRequestLabel);
        } catch(IOException e) {
            e.printStackTrace();
        }
        
        Issue issue = new Issue();
        issue.setTitle("Reviewer - " + reviewer.getLogin());
        List<Label> labels = new ArrayList<>();
        labels.add(reviewRequestLabel);
        issue.setLabels(labels);
        issue.setAssignee(reviewer);
        
        paper = pathToPaper + "/" + paper;
        String link = "http://pdfreviewhub.appspot.com/?repoName=" + repoName + "&writer=" + repoOwner + "&paper=" + paper;
        
        
        
        String downloadLink = "https://github.com/" + repoOwner + "/" + repoName + "/raw/master/" + paper;
        issue.setBody("@" + reviewer.getLogin() + " has been requested to review this paper by @"+reviewRequester.getLogin()+".\n" +
        			  "Click [here](" + downloadLink + ") to download the paper\n" +
        			  "Click [here](" + link + ") to upload your review.");
        
        issueService.createIssue(repoOwner, repoName, issue);
        return link;
    }

    private List<User> parseRequest(String body) throws JSONException, IOException {
        JSONObject data = new JSONObject(body);
        JSONArray reviewersJson = data.getJSONArray("reviewers");
        repoOwner = data.getString("owner");
        repoName = data.getString("repo");
        pathToPaper = data.getString("pathToPaper");
        paper = data.getString("paper");
        login = data.getString("login");
        
        setupGitClient(access_token);
        
        
        List<User> reviewers = new ArrayList<>();
        
        for(int i=0; i<reviewersJson.length(); i++) {
            reviewers.add(userService.getUser(reviewersJson.getString(i)));
        }
        return reviewers;
    }

    private void setupGitClient(String authToken) throws IOException {
        client = new GitHubClient();
        
        client.setOAuth2Token(authToken);	
        userService = new UserService(client);	
        reviewRequester = userService.getUser(login);
        issueService = new IssueService(client);
        RepositoryService repoService = new RepositoryService(client);
        repo = repoService.getRepository(repoOwner, repoName);
        collaboratorService = new CollaboratorService(client);
    }
	
	private void addCommentsToPDF(String pathToPaper, String paper) {
        // TODO Auto-generated method stub
        // Should add a comment to the front of the pdf with a memo about tags
    }

//    void addReviewToDatastore(String reviewer, String writer, String requester, String repo, String paper, String link) {
//		Entity request = new Entity("request");
//		request.setProperty("reviewer", reviewer);
//		request.setProperty("writer", writer);
//		request.setProperty("requester", requester);
//		request.setProperty("repo", repo);
//		request.setProperty("paper", paper);
//		request.setUnindexedProperty("link", link);
//		
//		DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
//		
//		datastore.put(request);
//	}
	
	@Override
	protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		// handle the DELETE request
	    GitHubClient client = new GitHubClient();
		client.setOAuth2Token(req.getParameter("access_token"));
		String writer = req.getParameter("writer");
		String reviewer = req.getParameter("reviewer");
		String repo = req.getParameter("repo");
		
		String closeComment = "@" + reviewer + " is no longer reviewing this paper.";
		ReviewSubmitServlet.closeReviewIssue(client, writer, repo, reviewer, closeComment);
		
		DBAbstraction database = DatabaseFactory.getDatabase();
		database.removeReviewFromDatastore(reviewer, writer, repo);
	}
	
//	static void removeReviewFromDatastore(String reviewer, String writer, String repo) {
//		DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
//
//		Query query = new Query("request");
//		FilterPredicate filterReviewer = new FilterPredicate("reviewer", FilterOperator.EQUAL, reviewer);
//		FilterPredicate filterWriter = new FilterPredicate("writer", FilterOperator.EQUAL, writer);
//		FilterPredicate filterRepo = new FilterPredicate("repo", FilterOperator.EQUAL, repo);
//		query.setFilter(CompositeFilterOperator.and(filterReviewer, filterWriter, filterRepo));
//		PreparedQuery preparedQuery = datastore.prepare(query);
//		for(Entity e : preparedQuery.asIterable()) {
//			datastore.delete(e.getKey());
//		}
//	}
}