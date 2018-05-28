package org.wangyl.framework.context;

import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.management.RuntimeErrorException;

import org.wangyl.framework.annotation.WYLAutowired;
import org.wangyl.framework.annotation.WYLController;
import org.wangyl.framework.annotation.WYLService;

/**
 * IOC容器
 * 
 * @author WlYlLj
 *
 */
public class WYLApplicationContext {

	private Map<String, Object> instanceMap = new ConcurrentHashMap<String, Object>();

	private String SCAN_PACKAGE = "scanPackage";

	private List<String> classCache = new ArrayList<String>();

	private Properties prop = new Properties();
	
	public WYLApplicationContext(String contextLocation) {
		InputStream in = null;
		try {
			// 1 定位
			in = this.getClass().getClassLoader().getResourceAsStream(contextLocation);
			// 2 载入
			prop.load(in);
			// 3 注册
			String scanPackage = prop.getProperty(SCAN_PACKAGE);
			doRegister(scanPackage);
			// 4 初始化
			doCreateBean();
			// 5 依赖注入
			populate();
			
		} catch (Exception e) {
			System.out.println(e.toString());
		} finally {
			if (null != in) {
				try {
					in.close();
				} catch (Exception e2) {
					System.out.println(e2.toString());
				}
			}
		}
	}

	/*
	 * 把制定注解的class保存到缓存中
	 */
	private void doRegister(String scanPackage) {
		URL url = this.getClass().getClassLoader().getResource("/" + scanPackage.replaceAll("\\.", "/"));
		System.out.println(url.getFile());
		File baseDir = new File(url.getFile());
		for (File file : baseDir.listFiles()) {
			if (file.isDirectory()) {
				doRegister(scanPackage + "." + file.getName());
			} else {
				classCache.add(scanPackage + "." + file.getName().replaceAll(".class", "").trim());
			}
		}
	}

	private void doCreateBean() {
		if(classCache.isEmpty()){
			return;
		}
		try {
			for(String className:classCache){
				Class<?> clazz = Class.forName(className);
				if(clazz.isAnnotationPresent(WYLController.class)){
					//类的全限定名作为benId
					String beanId = clazz.getName();
					instanceMap.put(beanId, clazz.newInstance());
				} else if(clazz.isAnnotationPresent(WYLService.class)){
					Object bean = clazz.newInstance();
					WYLService service = clazz.getAnnotation(WYLService.class);
					String value = service.value();
					//1 根据名称匹配
					if(!"".equals(value)){
						if(instanceMap.containsKey(value)){
							StringBuffer sb = new StringBuffer();
							sb.append("重复的beanId");
							sb.append(" " + value);
							throw new RuntimeException(sb.toString());
						}
						instanceMap.put(value, bean);
					}
					
					//2 根据类型匹配
					Class<?>[] interfaces = clazz.getInterfaces();
					for (Class<?> inter : interfaces) {
						instanceMap.put(inter.getName(), bean);
					}
				}
			}
		} catch (Exception e) {
			System.out.println(e.toString());
		}
	}
	/*
	 * 依赖注入
	 * @WYLAutowired
	 */
	private void populate() {
		if(instanceMap.isEmpty()){
			return;
		}
		for (Entry<String, Object> entry : instanceMap.entrySet()) {
			Object bean = entry.getValue();
			Field[] fields = bean.getClass().getDeclaredFields();
			for (Field field : fields) {
				if(field.isAnnotationPresent(WYLAutowired.class)){
					WYLAutowired autowired = field.getAnnotation(WYLAutowired.class);
					String beanId = "";
					String value = autowired.value().trim();
					if(!"".equals(value)){
						beanId = value;
					}else{
						beanId = field.getType().getName();
					}
					field.setAccessible(true);
					try {
						field.set(bean, instanceMap.get(beanId));
					} catch (Exception e) {
						System.out.println(e.toString());
					}
				}
			}
		}
	}
	
	public Map<String, Object> getAll() {
		return instanceMap;
	}
	
	public Object getBean(String name){
		return instanceMap.get(name);
	}

	public Properties getProp() {
		return prop;
	}
}
