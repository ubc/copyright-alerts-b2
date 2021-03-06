<%@ page language="java" contentType="text/html; charset=ISO-8859-1"
	pageEncoding="ISO-8859-1"%>
<%@ taglib prefix="bbNG" uri="/bbNG"%>

<bbNG:genericPage ctxId="ctx" entitlement="system.admin.VIEW">

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
<script src="//ajax.googleapis.com/ajax/libs/angularjs/1.2.15/angular.min.js"></script>
<script src="//ajax.googleapis.com/ajax/libs/angularjs/1.2.15/angular-resource.min.js"></script>
<script src="${ctx.request.contextPath}/static/systemconfig/systemconfig.js"></script>

<div id="alertsysconfig" ng-app="SystemConfigModule">
	<div class="section" ng-controller="StatusCtrl">
		<h1>Status</h1>
		<div ng-switch on="status.status">
		<h3 class="running" ng-switch-when="running">Running</h3>
		<h3 class="limit" ng-switch-when="limit">Time Limit Reached</h3>
		<h3 class="notrunning" ng-switch-when="stopped">Stopped</h3>
		<h3 class="runerror" ng-switch-when="error">Error During Run</h3>
		<h3 class="unavailable" ng-switch-default>Status Not Available</h3>
		</div>
		<ul class="stageIndicator">
			<li ng-class="{currentStage: (status.stage == 'queue')}">Queue Generation Stage</li>
			<li ng-class="{currentStage: (status.stage == 'newfiles')}">Adding New Files Stage</li>
			<li ng-class="{currentStage: (status.stage == 'update')}">Update Existing Index Stage</li>
		</ul>
		<!-- doesn't work in a multi-node environment'
		<input class="killjob" ng-show="status.status == 'running'" type="button" ng-click="stop()" value="Stop Current Running Job" />
		-->
		<h2>Progress</h2>
			<dl>
				<dt>Queue:</dt><dd>{{progress.queueCount}}</dd>
				<dt>Untagged Files:</dt><dd>{{progress.fileCount}}</dd>
			</dl>
			<input type="button" ng-click="getProgress()" value="Refresh Progress" />
		<h2>Last Run Statistics</h2>
			<dl>
				<dt>Runtime:</dt><dd>{{status.runtime}}</dd>
				<dt>Start:</dt><dd>{{status.runstart}}</dd>
				<dt>End:</dt><dd>{{status.runend}}</dd>
				<dt>You are currently on host:</dt><dd>{{status.host}}</dd>
				<dt>Alert generation is running on:</dt><dd>{{status.leader}}</dd>
			</dl>
	</div>

	<div class="section" ng-controller="ScheduleCtrl">
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
			<h2>Host</h2>
			<label>Run alert generation on:
				<select ng-model="host.leader" ng-options="opt for opt in host.options"></select>
			</label>
			<br />It may take up to 60 mins for all hosts to report themselves after the database is reset.
			<div class="savesection">
				<input class='save' type="button" ng-click="saveSchedule()" value="Save Schedule" />
				<span class="savemsg saving" ng-show='saving == "saving"'>Saving...</span>
				<span class="savemsg success" ng-show="saving == 'success'">Save Successful!</span>
				<span class="savemsg error" ng-show="saving == 'error'">Error Saving</span>
				<span class="savemsg error" ng-show="!loading && host.leader == ''">Please Select a Host.</span>
			</div>
		</form>
	</div>

	<div class="section" ng-controller="MetadataIdCtrl">
		<h1>Copyright Metadata Template</h1>
		<p>
		Select the Metadata Template used for copyright. A file is considered to be
		properly tagged if any of the boolean type attributes of the selected
		template has been set.
		</p>
		<!-- Note that one of the blackboard js libs start complaining about you not waiting for submits to finish if you press the "Enter" key for submit too
		many times. Shouldn't be a problem for normal use, there's only a few ids. -->
		<form name="templateform" ng-submit="submit()" class="templateform">
			<select ng-model="config.selected" ng-options="templateId as templateName for (templateId, templateName) in config.templatesList">
			</select>
			<input type="button" ng-click="submit()" value="Set Template" />
		</form>
		<div class="savesection">
			<span class="savemsg saving" ng-show='saving == "saving"'>Saving...</span>
			<span class="savemsg success" ng-show="saving == 'success'">Save Successful!</span>
			<span class="savemsg error" ng-show="saving == 'error'">Error Saving</span>
			<span class="savemsg error" ng-show="!loading && !config.selected">Please Select a Metadata Template.</span>
		</div>

		<div class="resetDatabase" ng-controller="ResetDatabaseController">
			<h2>Reset Database</h2>
			<form>
				<input type="button" value="Reset Database" ng-click="resetdbconfirm=!resetdbconfirm;"/>
			</form>
			<p ng-show="resetdbconfirm">
				Do you really want to reset the database?<br />
				<a ng-click="resetdb(); resetdbconfirm=false;" href="">Yes</a> /
				<a ng-click="resetdbconfirm=false;" href="">No</a>
			</p>
			<p>
				<span class="savemsg saving" ng-show='resetmsg == "reset"'>
					Resetting...
				</span>
				<span class="savemsg success" ng-show="resetmsg == 'success'">
					Reset successful, <strong>please refresh page!</strong>
				</span>
				<span class="savemsg error" ng-show="resetmsg == 'error'">
					Error resetting database.
				</span>
			</p>
		</div>
	</div>
</div>

</bbNG:genericPage>
