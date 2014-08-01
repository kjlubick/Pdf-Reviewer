package src.main.servlet;

import java.io.IOException;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.egit.github.core.Repository;
import org.eclipse.egit.github.core.RepositoryContents;
import org.eclipse.egit.github.core.User;
import org.eclipse.egit.github.core.client.GitHubClient;
import org.eclipse.egit.github.core.service.ContentsService;
import org.eclipse.egit.github.core.service.RepositoryService;
import org.eclipse.egit.github.core.service.UserService;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class FilesServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String repoName = req.getParameter("repo");
		String auth = req.getParameter("access_token");
		String path = req.getParameter("path");
		
		GitHubClient client = new GitHubClient();
		client.setOAuth2Token(auth);
		
		UserService userService = new UserService(client);
		User user = userService.getUser();
		String login = user.getLogin();
		
		RepositoryService repoService = new RepositoryService(client);
		Repository repo = repoService.getRepository(login, repoName);
		ContentsService contentsService = new ContentsService(client);
		List<RepositoryContents> contents = contentsService.getContents(repo, path);
		
		JSONArray json = new JSONArray();
		
		try {
			for(RepositoryContents file : contents) {
				JSONObject fileJson = new JSONObject();
				fileJson.put("type", file.getType());
				fileJson.put("name", file.getName());
				
				json.put(fileJson);
			}
		} catch(JSONException e) {}
		
		resp.setContentType("application/json");
		resp.getWriter().write(json.toString());
	}
}