<%@ page contentType="text/html; charset=UTF-8" %>
<%@ taglib prefix="bbNG" uri="/bbNG"%>

<bbNG:includedPage ctxId="ctx">

<style type="text/css">
#ubc_ctlt_ca_angular_div li
{
	border-top: 1px dotted #ccc;
	overflow: hidden;
	width: 100%;
}
#ubc_ctlt_ca_angular_div li a
{
	padding: 0.5em 0;
}
#ubc_ctlt_ca_angular_div li a.metadata-link
{
	float: left;
	word-wrap: break-word;
}
#ubc_ctlt_ca_angular_div li a.small-view
{
	font-size: 0.85em;
	font-style: italic;
	float: right;
}
#ubc_ctlt_ca_angular_div li:first-child
{
	border-top: none;
}
#ubc_ctlt_ca_angular_div li a:hover
{
	background: #ececec;
	text-decoration: none;
}
#ubc_ctlt_ca_angular_div h4
{
	padding-top: 0.5em;
}
#ubc_ctlt_ca_angular_div h4 a span
{
	color: #555;
}
#ubc_ctlt_ca_angular_div p.update
{
	font-size: 0.85em;
	margin-top: 0.75em;
}
#ubc_ctlt_ca_angular_div div.hideInitially
{
	display: none;
}
</style>

<div id="ubc_ctlt_ca_angular_div">
<div id="ubc_ctlt_ca_app" class="hideInitially" ng-controller="FileListCtrl">
	<p>These courses have files that needs to be copyright tagged.</p>
	<div ng-repeat="course in courseFiles.courses">
		<h4 class="moduleTitle">
			<a href="/bbcswebdav/courses/{{course.name}}">
				{{course.name}}
			</a>
			<a ng-click="course.show=!course.show">
				<span>({{course.numFiles}})</span>
				<img alt="Show files for {{course.name}}" ng-show="!course.show"
					src="/images/ci/ng/cm_arrow_down.gif" />
				<img alt="Hide files for {{course.name}}" ng-show="course.show"
					src="/images/ci/ng/cm_arrow_up.gif" />
			</a>
		</h4>
		<ul ng-show="course.show">
			<li ng-repeat="file in course.files">
			<a class="metadata-link" href="/webapps/ubc-metadata-BBLEARN//metadata/list?path={{file.encodedPath}}">{{file.name}}</a>
			<a class="small-view" href="/bbcswebdav{{file.rawPath}}">(view)</a>
			</li>
		</ul>
	</div>
	<p class='update'>Updated: {{lastupdate}}</p>
</div>
</div>

<bbNG:jsBlock>
<script type="text/javascript">

function FileListCtrl($scope, Files, Status) 
{
	$scope.courseFiles = Files.get(
		function(val)
		{ // show the alerts if the user actually has untagged files
			if (val.courses.length > 0)
			{ 
				$('ubc_ctlt_ca_app').toggleClassName('hideInitially');
			}
		}
	);
	$scope.lastupdate = "Retrieving...";
	$scope.status = Status.get(
		function(val)
		{
			if (val.runend == '-')
				$scope.lastupdate = "Currently Running";
			else
				$scope.lastupdate = val.runend;
		}
	);
};

// Need a separate function to start angularjs cause Blackboard's javascript
// loader delays loading libraries until the entire page has been loaded.
function startAngular() 
{
	// to prevent an exception when angular first loads, we don't declare
	// an ng-app until we're sure angular + extensions have all been loaded
	$("ubc_ctlt_ca_angular_div").setAttribute('ng-app', 'CopyAlertsModule');
	var services = angular.module('CopyAlertsModuleServices', ['ngResource']);
	var apiUrlPrefix = '/webapps/ubc-copyright-alerts-BBLEARN/alertsmodule';
	services.factory('Files', 
		function($resource)
		{
			return $resource('/webapps/ubc-copyright-alerts-BBLEARN/alertsmodule/files');
		}
	);
	services.factory('Status', 
		function($resource) 
		{
			return $resource('/webapps/ubc-copyright-alerts-BBLEARN/systemconfig/status/status');
		}
	);
	angular.module('CopyAlertsModule', ['CopyAlertsModuleServices']);

	angular.bootstrap($("ubc_ctlt_ca_angular_div"), ['CopyAlertsModule']);
}

/***** ANGULARJS LIBRARY LOADER *****/
/* This might not be needed but it works and I'm not too sure what Blackboard's
 * javascript loader is doing, so better safe than sorry. */

// lets you pre-set arguments in a javascript function, for passing around
// functions like a variable
function partial(func /*, 0..n args */) 
{
	var args = Array.prototype.slice.call(arguments, 1);
	return function() 
	{
		var allArguments = args.concat(Array.prototype.slice.call(arguments));
		return func.apply(this, allArguments);
	};
}

// load a library dynamically, execute a call back function once done
function loadFile(url, onloadcb) 
{
	var head = document.getElementById('ubc_ctlt_ca_angular_div');
	var script = document.createElement('script');
	script.src = url;
	script.onload = onloadcb;
	script.onerror = function() { console.log("Error loading library: " + url); };
	script.onreadystatechange = onloadcb; // cause IE just has to be contrary for no reason
	head.parentNode.insertBefore(script, head);
}

// given a chain of libraries, load them sequentially
// will only load libraries that haven't been loaded yet
function libLoader(libs, after)
{
	if (typeof after == 'function') after();
	if (libs.length == 0) return; // base case
	var lib = libs.shift();
	if (lib.loaded())
	{ // move on to next library
		libLoader(libs);
	}
	else
	{ // load the library and then move on
		if ('after' in lib) 
			loadFile(lib.url, partial(libLoader, libs, lib.after));
		else 
			loadFile(lib.url, partial(libLoader, libs, null));
	}
}
// load AngularJS and associated extensions
libLoader([
	{
	"loaded": function() { return typeof angular != 'undefined'}, 
	"url": "//ajax.googleapis.com/ajax/libs/angularjs/1.1.1/angular.min.js"
	},
	{
	"loaded": function() { 
			try { angular.module("ngResource"); } 
			catch(e) { return false; } 
			return true; 
		}, 
	"url": "//ajax.googleapis.com/ajax/libs/angularjs/1.1.1/angular-resource.min.js",
	"after": partial(startAngular)
	}
]);
	
</script>
</bbNG:jsBlock>
</bbNG:includedPage>
