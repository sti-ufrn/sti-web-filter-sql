package br.ufrn.sti.web.filters.sql;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import br.ufrn.sti.web.filters.sql.util.AntiSQLUtil;
import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;

/**
 * AntiSQLFilter is a J2EE Web Application Filter that protects web components from SQL Injection hacker attacks.<br>
 * Must to be configured with web.xml descriptors.
 * <br><br>
 * Below, the filter initialization parameters to configure:
 * <br><br>
 * <b>logging</b> - a <i>true</i> value enables output to Servlet Context logging in case of a SQL Injection detection.
 * Defaults to <i>false</i>.
 * <br><br>
 * <b>behavior</b> - there are three possible behaviors in case of a SQL Injection detection:
 * <li> protect : (default) dangerous SQL keywords are 2nd character supressed /
 * dangerous SQL delimitters are blank space replaced.
 * Afterwards the request flows as expected.
 * <li> throw: a ServletException is thrown - breaking the request flow.
 * <li> forward: thre request is forwarded to a specific resource.
 * <br><br>
 * <b>forwardTo</b> - the resource to forward when forward behavior is set.<br>
 *
 * @author rbellia
 * @author Johnny Marçal (johnnycms@gmail.com)
 * @author Arlindo Rodrigues (arlindonatal@gmail.com)
 * @author Raphael Medeiros (raphael.medeiros@gmail.com)
 *
 * @version 0.1
 */
public class AntiSQLFilter implements Filter {

	private static final String INIT_PARAM_LOGGING = "logging";
	private static final String INIT_PARAM_BEHAVIOR = "behavior";
	private static final String INIT_PARAM_FORWARDTO = "forwardTo";

	private static final String BEHAVIOR_PROTECT = "protect";
	private static final String BEHAVIOR_THROW = "throw";
	private static final String BEHAVIOR_FORWARD = "forward";

	private static long attempts = 0;

	private FilterConfig filterConfig;
	private List<String> excludedUrls = new ArrayList<String>();

	private Logger logger = Logger.getLogger(this.getClass());

	public void init(FilterConfig filterConfig) {
		this.filterConfig = filterConfig;
		String excludePattern = filterConfig.getInitParameter("excludedUrls");
		if (excludePattern != null && !excludePattern.isEmpty())
			excludedUrls = Arrays.asList(excludePattern.split(","));
	}

	public void doFilter(ServletRequest req, ServletResponse resp, FilterChain filterChain) throws IOException, ServletException {

		HttpServletRequest httpServletRequest = (HttpServletRequest) req;
		try {

			String path = httpServletRequest.getRequestURI();
			path = path.substring(httpServletRequest.getContextPath().length());

			if (!excludedUrls.contains(path)) {

				ResettableStreamHttpServletRequest resettableRequest = new ResettableStreamHttpServletRequest(httpServletRequest);
				String body = IOUtils.toString(resettableRequest.getReader());
				boolean isUnsafeParams = AntiSQLUtil.isUnsafe(resettableRequest.getParameterMap());
				boolean isUnsafeBody = AntiSQLUtil.isUnsafe(body);

				if (isUnsafeParams || isUnsafeBody) {

					String pLogging = filterConfig.getInitParameter(INIT_PARAM_LOGGING);
					if (pLogging != null && pLogging.equalsIgnoreCase("true")) {
						registrarLogOcorrenciaParametrosSuspeitos(resettableRequest, body);
					}

					String behavior = filterConfig.getInitParameter(INIT_PARAM_BEHAVIOR);
					String forwardTo = filterConfig.getInitParameter(INIT_PARAM_FORWARDTO);

					if (!isUnsafeBody && behavior != null && behavior.equalsIgnoreCase(BEHAVIOR_PROTECT)) {
						resettableRequest.resetInputStream();
						HttpServletRequest wrapper = new AntiSQLRequest(resettableRequest);
						filterChain.doFilter(wrapper, resp);
					}
					else if (behavior != null && behavior.equalsIgnoreCase(BEHAVIOR_FORWARD) && forwardTo != null) {
						HttpServletResponse currentResponse = (HttpServletResponse) resp;
						currentResponse.setStatus(HttpServletResponse.SC_CONTINUE);
						RequestDispatcher dd = resettableRequest.getRequestDispatcher(forwardTo);
						dd.forward(resettableRequest, currentResponse);
					}
					else {
						throw new ServletException("SQL Injection Detected!");
					}

				}
				else {
					resettableRequest.resetInputStream();
					filterChain.doFilter(resettableRequest, resp);
				}

			}
			else {
				filterChain.doFilter(httpServletRequest, resp);
			}

		}
		catch (Exception e) {
			logger.error("Erro no tratamento de SQL Injection.", e);
			filterChain.doFilter(req, resp);
		}

	}

	private static class ResettableStreamHttpServletRequest extends HttpServletRequestWrapper {

		private byte[] rawData;
		private HttpServletRequest request;
		private ResettableServletInputStream servletStream;

		public ResettableStreamHttpServletRequest(HttpServletRequest request) {
			super(request);
			this.request = request;
			this.servletStream = new ResettableServletInputStream();
		}


		public void resetInputStream() {
			servletStream.stream = new ByteArrayInputStream(rawData);
		}

		@Override
		public ServletInputStream getInputStream() throws IOException {
			if (rawData == null) {
				rawData = IOUtils.toByteArray(this.request.getReader());
				servletStream.stream = new ByteArrayInputStream(rawData);
			}
			return servletStream;
		}

		@Override
		public BufferedReader getReader() throws IOException {
			if (rawData == null) {
				rawData = IOUtils.toByteArray(this.request.getReader());
				servletStream.stream = new ByteArrayInputStream(rawData);
			}
			return new BufferedReader(new InputStreamReader(servletStream));
		}


		private class ResettableServletInputStream extends ServletInputStream {

			private InputStream stream;

			@Override
			public int read() throws IOException {
				return stream.read();
			}
		}
	}

	private void registrarLogOcorrenciaParametrosSuspeitos(HttpServletRequest originalRequest, String body) {

		StringBuilder sb = new StringBuilder();
		sb.append("\nPossible SQL injection attempt #" + (++attempts) + " at " + new java.util.Date());
		sb.append("\nRemote Address: " + originalRequest.getRemoteAddr());
		sb.append("\nRemote User: " + originalRequest.getRemoteUser());
		sb.append("\nSession Id: " + originalRequest.getRequestedSessionId());
		sb.append("\nURI: " + originalRequest.getContextPath() + originalRequest.getRequestURI());
		sb.append("\nParameters via " + originalRequest.getMethod());
		Map paramMap = originalRequest.getParameterMap();
		if (paramMap != null) {
			for (Iterator iter = paramMap.keySet().iterator(); iter.hasNext(); ) {
				String paramName = (String) iter.next();
				String[] paramValues = originalRequest.getParameterValues(paramName);
				sb.append("\n\t" + paramName + " = ");
				for (int j = 0; j < paramValues.length; j++) {
					sb.append(paramValues[j]);
					if (j < paramValues.length - 1) {
						sb.append(" , ");
					}
				}
			}
		}

		if (body != null && !body.isEmpty())
			sb.append("\nBody: " + body);

		logger.error(sb);

	}

	public void destroy() {
	}

}
