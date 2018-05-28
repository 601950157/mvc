package org.wangyl.framework.servlet;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.wangyl.framework.annotation.WYLController;
import org.wangyl.framework.annotation.WYLRequestMapping;
import org.wangyl.framework.annotation.WYLRequestParam;
import org.wangyl.framework.context.WYLApplicationContext;
import org.wangyl.framework.mdoel.WYLModelAndView;

/**
 * 控制器，MVC框架的入口
 * 
 * @author wangyl
 *
 */
public class WYLDispatcherServlet extends HttpServlet {

	private static final long serialVersionUID = 1L;

	private String CONTEXT = "contextLocation";

	private String TEMPLATE = "templateDir";
	/*
	 * 存放controller中所有的方法，该类中的成员变量pattern为映射url的正则
	 */
	private List<Handler> handlerMappings = new ArrayList<Handler>();

	private Map<Handler, HandlerAdapter> adapterMappings = new HashMap<Handler, HandlerAdapter>();

	private List<ViewResolver> viewResolvers = new ArrayList<ViewResolver>();

	/*
	 * 初始化方法
	 */
	@Override
	public void init(ServletConfig servletConfig) throws ServletException {
		// 初始化IOC容器
		String contextLocation = servletConfig.getInitParameter(CONTEXT);
		WYLApplicationContext context = new WYLApplicationContext(contextLocation);

		// 解析url和Method的关联关系
		initHandlerMappings(context);
		// 解析Handler与HandlerAdaper映射关系，HandlerAdaper包含参数列表
		initHandlerAdapters(context);
		// 初始化模板文件
		initViewResolvers(context);

		System.out.println("wyl MVC init...");
	}

	/**
	 * handlerMapping找到controller中的所有的<url,Method>对应关系
	 * 
	 * @author Wangyl
	 */
	private void initHandlerMappings(WYLApplicationContext context2) {
		Map<String, Object> beans = context2.getAll();
		if (beans.isEmpty()) {
			return;
		}
		for (Entry<String, Object> entry : beans.entrySet()) {
			Object bean = entry.getValue();
			Class<?> clazz = bean.getClass();
			if (!clazz.isAnnotationPresent(WYLController.class)) {
				continue;
			}
			String url = "";
			if (clazz.isAnnotationPresent(WYLRequestMapping.class)) {
				WYLRequestMapping requestMapping = clazz.getAnnotation(WYLRequestMapping.class);
				url = requestMapping.value();
				Method[] methods = clazz.getMethods();
				for (Method method : methods) {
					if (method.isAnnotationPresent(WYLRequestMapping.class)) {
						WYLRequestMapping requestMapping2 = method.getAnnotation(WYLRequestMapping.class);
						String regex = url + requestMapping2.value().replaceAll("/+", "/");
						Pattern pattern = Pattern.compile(regex);
						handlerMappings.add(new Handler(bean, method, pattern));
					}
				}
			}
		}
	}

	/**
	 * handlerAdapters将Method方法中的参数
	 * 
	 */
	private void initHandlerAdapters(WYLApplicationContext context2) {
		if (handlerMappings.isEmpty()) {
			return;
		}
		// 参数类型顺序
		Map<String, Integer> paramMapping = new HashMap<String, Integer>();

		for (Handler handler : handlerMappings) {
			// 方法
			Method method = handler.method;
			// 方法上所有参数
			Class<?>[] parameterTypes = method.getParameterTypes();
			// request和response的排列顺序
			for (int i = 0; i < parameterTypes.length; i++) {
				Class<?> clazz = parameterTypes[i];
				if (clazz == HttpServletRequest.class || clazz == HttpServletResponse.class) {
					paramMapping.put(parameterTypes[i].getName(), i);
				}
			}
			// @wylRequestParam排列顺序
			Annotation[][] methodAnnotations = method.getParameterAnnotations();
			for (int i = 0; i < methodAnnotations.length; i++) {
				Annotation[] annotations = methodAnnotations[i];
				for (Annotation annotation : annotations) {
					if (annotation instanceof WYLRequestParam) {
						String params = ((WYLRequestParam) annotation).value();
						if (!"".equals(params.trim())) {
							paramMapping.put(params, i);
						}
					}
				}
			}
			adapterMappings.put(handler, new HandlerAdapter(paramMapping));
		}

	}

	/*
	 * 视图解析器
	 */
	private void initViewResolvers(WYLApplicationContext context2) {
		Properties prop = context2.getProp();
		String template = prop.getProperty(TEMPLATE);
		String baseDir = this.getClass().getClassLoader().getResource(template).getFile();
		File file = new File(baseDir);
		for (File templateFile : file.listFiles()) {
			viewResolvers.add(new ViewResolver(templateFile.getName(), templateFile));
		}
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		try {
			doDispatch(req, resp);
		} catch (Exception e) {
			resp.getWriter().write("exception is:" + e.toString());
		}
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		this.doGet(req, resp);
	}

	/*
	 * 处理业务请求
	 */
	private void doDispatch(HttpServletRequest req, HttpServletResponse resp) throws Exception {
		// 1
		Handler handler = getHandler(req);
		if (handler == null) {
			resp.getWriter().write("404 Not Found");
		}
		// 2
		HandlerAdapter handlerAdapter = getHandlerAdapter(handler);

		// 3
		WYLModelAndView modelAndView = handlerAdapter.handle(req, resp, handler);

		// 4
		applyDefaultViewName(resp, modelAndView);

	}

	private Handler getHandler(HttpServletRequest req) {
		if (handlerMappings.isEmpty()) {
			return null;
		}

		String requestURI = req.getRequestURI();
		String contextPath = req.getContextPath();
		requestURI = requestURI.replaceAll(contextPath, "").replaceAll("/+", "/");
		for (Handler handler : handlerMappings) {
			Pattern pattern = handler.pattern;
			System.out.println("pattern "+pattern.toString());
			Matcher matcher = pattern.matcher(requestURI);
			if (!matcher.matches()) {
				continue;
			}
			return handler;
		}
		return null;
	}

	private HandlerAdapter getHandlerAdapter(Handler handler) {
		if (adapterMappings.isEmpty()) {
			return null;
		}
		return adapterMappings.get(handler);
	}

	private void applyDefaultViewName(HttpServletResponse resp, WYLModelAndView mv) throws Exception {
		if (null == mv) {
			return;
		}
		if (viewResolvers.isEmpty()) {
			return;
		}

		for (ViewResolver resolver : viewResolvers) {
			if (!mv.getView().equals(resolver.view)) {
				continue;
			}
			String r = resolver.parse(mv);
			if (r != null) {
				resp.getWriter().write(r);
				break;
			}
		}
	}

	/*
	 * 处理器定义
	 */
	private class Handler {
		protected Object controller;
		protected Method method;
		protected Pattern pattern;

		public Handler(Object controller, Method method, Pattern pattern) {
			this.controller = controller;
			this.method = method;
			this.pattern = pattern;
		}
	}

	/*
	 * 处理器适配器
	 */
	private class HandlerAdapter {

		Map<String, Integer> paramMapping;

		public HandlerAdapter(Map<String, Integer> paramMapping) {
			this.paramMapping = paramMapping;
		}

		// 传递rep和resp是为了给方法赋值,传递handler是为了给自定义参数赋值
		WYLModelAndView handle(HttpServletRequest req, HttpServletResponse resp, Handler handler) throws Exception {
			Class<?>[] parameterTypes = handler.method.getParameterTypes();
			// 值列表
			Object[] paramValues = new Object[parameterTypes.length];
			// 获取传递的参数列表
			Map<String, String[]> parameterMap = req.getParameterMap();
			for (Entry<String, String[]> param : parameterMap.entrySet()) {
				String paramName = param.getKey();
				String paramValue = Arrays.toString(param.getValue()).replaceAll("\\[|\\]", "").replaceAll(",\\s", ",");
				if (!this.paramMapping.containsKey(paramName)) {
					continue;
				}
				Integer index = this.paramMapping.get(paramName);
				paramValues[index] = paramValue;
			}
			// rep和resp赋值
			if (this.paramMapping.containsKey(HttpServletRequest.class.getName())) {
				Integer reqIndex = this.paramMapping.get(HttpServletRequest.class.getName());
				paramValues[reqIndex] = req;
			}
			if (this.paramMapping.containsKey(HttpServletResponse.class.getName())) {
				Integer respIndex = this.paramMapping.get(HttpServletResponse.class.getName());
				paramValues[respIndex] = resp;
			}
			Class<?> returnType = handler.method.getReturnType();
			if (returnType == WYLModelAndView.class) {
				Object value = handler.method.invoke(handler.controller, paramValues);
				return (WYLModelAndView) value;
			}
			return null;
		}

	}

	private class ViewResolver {

		protected String view;
		protected File file;

		public ViewResolver(String view, File file) {
			this.view = view;
			this.file = file;
		}

		public String parse(WYLModelAndView mv) throws Exception {
			
			StringBuffer sb = new StringBuffer();

			RandomAccessFile ra = new RandomAccessFile(this.file, "r");
			try {
				String line = null;
				while (null != (line = ra.readLine())) {
					Matcher m = matcher(line);
					while (m.find()) {
						for (int i = 1; i <= m.groupCount(); i++) {
							String paramName = m.group(i);
							Object paramValue = mv.getModel().get(paramName);
							if (null == paramValue) {
								continue;
							}
							line = line.replaceAll("@\\{" + paramName + "\\}", paramValue.toString());
						}
					}
					sb.append(line);
				}
			} finally {
				ra.close();
			}
			return sb.toString();
		}
	}
	
	private Matcher matcher(String str){
		Pattern pattern = Pattern.compile("@\\{(.+?)\\}",Pattern.CASE_INSENSITIVE);
		Matcher m = pattern.matcher(str);
		return m;
	}
}