package br.ufrn.sti.web.filters.sql.util;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Classe com métodos utilitários para verificações efetuadas no filter.
 *
 * @author Johnny Marçal (johnnycms@gmail.com)
 * @author Arlindo Rodrigues (arlindonatal@gmail.com)
 * @author Raphael Medeiros (raphael.medeiros@gmail.com)
 */
public class AntiSQLUtil {

	private static String[] keyWords = {"\"", "\'", "/*", "*/", "--", "exec",
			"select", "update", "delete", "insert",
			"alter", "drop", "create", "shutdown"};

	private static String[] keyWordsSQL = {"exec",
			"select", "update", "delete", "insert",
			"alter", "drop", "create", "shutdown", "or", "and", "like"};

	public static boolean isUnsafe(Map parameterMap) {
		Map newMap = new HashMap();
		if (parameterMap != null) {
			Iterator iter = parameterMap.keySet().iterator();
			while (iter.hasNext()) {
				String key = (String) iter.next();
				String[] param = (String[]) parameterMap.get(key);
				for (int i = 0; i < param.length; i++) {
					if (isUnsafe(param[i])) return true;
				}
			}
		}
		return false;
	}

	public static boolean isUnsafe(String value) {
		if (value != null) {
			String lowerCase = value.toLowerCase();
			for (int i = 0; i < keyWordsSQL.length; i++) {
				if ((lowerCase.indexOf(" " + keyWordsSQL[i]) >= 0 || lowerCase.indexOf(keyWordsSQL[i] + " ") >= 0)
						&& ((lowerCase.startsWith("'") || lowerCase.startsWith("%")) || lowerCase.endsWith("--"))) {
					return true;
				}
			}
		}
		return false;
	}

	public static Map getSafeParameterMap(Map parameterMap) {
		Map newMap = new HashMap();
		Iterator iter = parameterMap.keySet().iterator();
		while (iter.hasNext()) {
			String key = (String) iter.next();
			String[] oldValues = (String[]) parameterMap.get(key);
			String[] newValues = new String[oldValues.length];
			for (int i = 0; i < oldValues.length; i++) {
				newValues[i] = getSafeValue(oldValues[i]);
			}
			newMap.put(key, newValues);
		}
		return Collections.unmodifiableMap(newMap);
	}

	public static String getSafeValue(String oldValue) {
		StringBuffer sb = new StringBuffer(oldValue);
		String lowerCase = oldValue.toLowerCase();
		for (int i = 0; i < keyWords.length; i++) {
			int x = -1;
			while ((x = lowerCase.indexOf(keyWords[i])) >= 0) {
				if (keyWords[i].length() == 1) {
					sb.replace(x, x + 1, " ");
					lowerCase = sb.toString().toLowerCase();
					continue;
				}
				sb.deleteCharAt(x + 1);
				lowerCase = sb.toString().toLowerCase();
			}
		}
		return sb.toString();
	}

}
