<%@ page language="java" contentType="text/html; charset=ISO-8859-1"
	pageEncoding="ISO-8859-1"%>
<%@ taglib prefix="bbNG" uri="/bbNG"%>

<bbNG:genericPage ctxId="ctx" >

<bbNG:pageHeader instructions="Configure the Copyright Alerts building block.">
	<bbNG:pageTitleBar >Copyright Alerts Settings</bbNG:pageTitleBar>
</bbNG:pageHeader>
<link href="${ctx.request.contextPath}/static/jqcron/jqCron.css" type="text/css" rel="stylesheet" />
<link href="${ctx.request.contextPath}/static/style.css" type="text/css" rel="stylesheet" />

<script src="//ajax.googleapis.com/ajax/libs/jquery/1.10.1/jquery.min.js"></script>
<script>
jQuery.noConflict();
</script>
<script src="${ctx.request.contextPath}/static/jqcron/jqCron.min.js"></script>
<script src="//ajax.googleapis.com/ajax/libs/angularjs/1.0.6/angular.min.js"></script>
<script src="//ajax.googleapis.com/ajax/libs/angularjs/1.0.6/angular-resource.min.js"></script>
<script src="${ctx.request.contextPath}/static/systemconfig/controllers.js"></script>

<div id="alertsysconfig" ng-app="SystemConfigModule">
	<div ng-controller="ScheduleCtrl">
		<h1>Schedule Alert Generation</h1>
		<span ng-show="loading">Loading alert preferences, please wait.</span>
		<form name="scheduleform">
			<h2>Enable</h2>
				<label class='radiolabel'><input name='enable' ng-model='schedule.enable' type="radio" value="true" />On</label>
				<label class='radiolabel'><input name='enable' ng-model='schedule.enable' type="radio" value="false" checked />Off</label>
			<h2>Start</h2>
			<input id="croninput" type="text" ng-model="schedule.cron" jqcronui />
			<h2>Runtime</h2>
			<label for='limit'>Limit runtime </label><input id='limit' type='checkbox' ng-model='schedule.limit' />
			<div ng-show='schedule.limit'>
				<label for='hours'>Hours:</label><input id='hours' name='hours' type="text" ng-model="schedule.hours" integer />
				<label for='minutes'>Minutes:</label><input id='minutes' name='minutes' type="text" ng-model="schedule.minutes" integer/>

				<span ng-show="scheduleform.hours.$error.integer">Hours and minutes needs to be a number.</span>
			</div>
			<button class='save' type="button" ng-click="saveSchedule(schedule)">Save Schedule</button>
		</form>
	</div>
	<div>
		<h1>Status</h1>
	</div>
</div>

</bbNG:genericPage>
