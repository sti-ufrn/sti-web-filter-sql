package br.ufrn.sti.web.filters.sql;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import java.util.Map;

import br.ufrn.sti.web.filters.sql.util.AntiSQLUtil;

/**
 *
 * AntiSQLRequest wraps the original HTTP request to provide
 * safe parameters values when protect behavior is set.
 *
 * @author rbellia
 * @version 0.1
 *
 */

public class AntiSQLRequest extends HttpServletRequestWrapper {

	private HttpServletRequest original;
	private Map safeParameterMap;

	/**
	 * Constructor to wrap the original HttpServletRequest
	 *
	 * @param req the original request to be wrapped
	 */
	public AntiSQLRequest(HttpServletRequest req) {
		super(req);
		original = req;
	}


	/**
	 *
	 * Returns a single safe versioned request parameter value.
	 *
	 * @param paramName the request parameter name
	 * @return a single safe versioned request value
	 *
	 */
	public String getParameter(String paramName) {
		String[] values = getParameterValues(paramName);
		if (values != null && values.length > 0) {
			return values[0];
		}
		else {
			return null;
		}
	}

	/**
	 *
	 * Returns an array of safe versioned request values.
	 *
	 * @param paramName the request parameter name
	 * @return an array of versioned request values
	 *
	 */
	public String[] getParameterValues(String paramName) {
		return (String[]) getParameterMap().get(paramName);
	}

	/**
	 *
	 * Returns a safe versioned request parameter Map.
	 *
	 * @return a safe versioned request parameter Map.
	 *
	 */
	public Map getParameterMap() {
		if (safeParameterMap == null) {
			Map originalParameterMap = original.getParameterMap();
			safeParameterMap = AntiSQLUtil.getSafeParameterMap(originalParameterMap);
		}
		return safeParameterMap;
	}

}
