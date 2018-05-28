package org.wangyl.app.controller;

import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.wangyl.app.service.IOCNameService;
import org.wangyl.app.service.IOCService;
import org.wangyl.framework.annotation.WYLAutowired;
import org.wangyl.framework.annotation.WYLController;
import org.wangyl.framework.annotation.WYLRequestMapping;
import org.wangyl.framework.annotation.WYLRequestParam;
import org.wangyl.framework.mdoel.WYLModelAndView;

@WYLController
@WYLRequestMapping("/app")
public class IOCController {

	@WYLAutowired
	private IOCService iocService;
	
	@WYLAutowired("ioc")
	public IOCNameService iocNameService;
	
	@WYLRequestMapping("/.*.do")
	public WYLModelAndView test(HttpServletRequest rep,
			HttpServletResponse resp,
			@WYLRequestParam("name") String name,
			@WYLRequestParam("result") String result){
		Map<String,Object> model = new HashMap<String,Object>();
		model.put("name", name);
		model.put("result", result);
		WYLModelAndView mv = new WYLModelAndView("wyl.fr", model);
		return mv;
	}
}
