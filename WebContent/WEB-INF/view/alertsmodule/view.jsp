<%@ page contentType="text/html; charset=UTF-8" %>
<%@ taglib prefix="bbNG" uri="/bbNG"%>

<bbNG:includedPage ctxId="ctx">
	<script src="//ajax.googleapis.com/ajax/libs/angularjs/1.0.6/angular.min.js" type="text/javascript"></script>
	<script src="//ajax.googleapis.com/ajax/libs/angularjs/1.0.6/angular-resource.min.js" type="text/javascript"></script>
	<script src="${ctx.request.contextPath}/static/module/module.js" type="text/javascript"></script>
<div id="div_copyright_alerts" ng-app="AlertsModule">
	<div ng-controller="FileListCtrl">
		<p>Hello World!</p>
		{{files.test}}
	</div>
	<script type="text/javascript">
	if (jQuery) 
	{
		console.log("jQuery is loaded");
	}
	else
	{
		console.log("jQuery is not loaded");
	}
	</script>
</div>
</bbNG:includedPage>