package org.wangyl.app.service;

import org.wangyl.framework.annotation.WYLService;

@WYLService
public class IOCServiceImpl implements IOCService {

	public String sayIOC() {
		return "wangyl is very smart";
	}

}
