package edu.ncsu.dlf.servlet;

import java.io.IOException;
import java.net.URISyntaxException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import edu.ncsu.dlf.utils.HttpUtils;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.HttpClients;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Completes authetication of the GitHub user through GitHub's OAuth API. 
 */
public class LoginServlet extends HttpServlet {

	private static final long serialVersionUID = 1L;

	/**
	 * Completes the second step of GitHub OAuth 2 web application flow
	 * https://developer.github.com/apps/building-oauth-apps/authorization-options-for-oauth-apps/#web-application-flow
	 * @param req HTTP request
	 * @param res HTTP response
	 */
	@Override
	public void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

		//So if someone hits /login without going through our flow,
		//they will just see the client_id displayed on the page
		//TODO: Display error page instead?
		if (req.getParameter("code") == null) {
		    resp.setContentType("application/json");
		    JSONObject jobj = new JSONObject();
		    try {
		        jobj.put("client_id", System.getenv("GITHUB_ID"));
	          jobj.write(resp.getWriter());
		    } catch (JSONException e) {
		        e.printStackTrace();
		        resp.sendError(500);
		    }
		    return;
		}

	 //Make a POST to the GitHub OAuth API, with the code received from the previous page
	  HttpPost request = null;
		try {
			URIBuilder builder = new URIBuilder("https://github.com/login/oauth/access_token");
			builder.addParameter("client_id", System.getenv("GITHUB_ID"));
			builder.addParameter("client_secret", System.getenv("GITHUB_API"));
			builder.addParameter("code", req.getParameter("code"));

			request = new HttpPost(builder.build());
			request.setHeader("accept", "application/json");

			HttpClient client = HttpClients.createDefault();
			HttpResponse authResponse = client.execute(request);

			String accessToken = "";
			//Extracts the access_token from the response
			try {
				JSONObject responseJSON = new JSONObject(HttpUtils.getResponseBody(authResponse));
				accessToken = (String) responseJSON.get("access_token");
			} catch (JSONException e) {
				e.printStackTrace();
			}

			//Redirects to the tool page with the GitHub access token as a URL parameter
			resp.sendRedirect(req.getContextPath() + "/tool?access_token=" + accessToken);

		} catch(URISyntaxException e) {
			e.printStackTrace();
		} finally{
			if (request != null) {
				request.releaseConnection();
			}
		}
	}

}
