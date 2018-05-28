package org.wangyl.app.service;

import org.wangyl.framework.annotation.WYLService;

@WYLService("ioc")
public class IOCNameServiceImpl implements IOCNameService {

	public String sayHello() {
		return "I am very good";
	}

}
