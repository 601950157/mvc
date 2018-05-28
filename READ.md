# mvc
### spring mvc的一种简易实现，旨在帮助开发人员深入理解spring MVC的执行原理

### 启动

```
mvn jetty:run
```

### 访问url

```
http://{host}:8080/app/*.do
```

### 演示demo

```
 org.wangyl.app
```

### 实现框架

```
org.wangyl.framework
```

### 实现入口

```
org.wangyl.framework.servlet.WYLDispatcherServlet
```

### 实现思路

```
initHandlerMappings()
```
将url与Method方法的映射关系初始化,它的描述信息为类Handler,该类中由具体对象controller、对象中的方法method、正则pattern构成；
```
initHandlerAdapters() 
```
将方法中参数顺序保存起来
```
initViewResolvers()
```
将对应的模板文件全部初始化
