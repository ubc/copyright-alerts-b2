<%@ page contentType="text/html; charset=UTF-8" %>
<%@ taglib prefix="bbNG" uri="/bbNG"%>

<bbNG:includedPage ctxId="ctx">
<div id="ubc_ctlt_ca_angular_div">
<div id="ubc_ctlt_ca_app" class="hideInitially" ng-controller="FileListCtrl">
	<p>These courses have files that needs to be copyright tagged.</p>
	<div ng-repeat="(cid, course) in courseFiles.courses">
		<h4 class="moduleTitle">
			<a ng-click="course.show=!course.show" href="" >
				{{course.title}}
				<span>({{course.numFiles}})</span>
				<img alt="Show files for {{course.title}}" ng-show="!course.show"
					src="/images/ci/ng/cm_arrow_down.gif" />
				<img alt="Hide files for {{course.title}}" ng-show="course.show"
					src="/images/ci/ng/cm_arrow_up.gif" />
				<a class="side-controls" href="/bbcswebdav/courses/{{course.name}}">
					(view)
				</a>
			</a>
		</h4>
		<ul ng-show="course.show">
			<li ng-repeat="file in course.files">
			<a class="main-link" href="/webapps/ubc-metadata-BBLEARN//metadata/list?path={{file.encodedPath}}">{{file.name}}</a>
			<a class="side-controls" href="/bbcswebdav{{file.rawPath}}">(view)</a>
			</li>
			<li ng-if="course.numPages > 1" class="rumble inventory_paging">
				<a class="pagelink" title="First Page" href="" ng-click="getPage(course, 1);" ng-hide="course.page == 1">
					<img src="/images/ci/ng/small_rewind.gif" alt="First Page">
				</a>
				<a class="pagelink" title="Previous Page" href="" ng-click="getPage(course, course.page - 1);" ng-hide="course.page == 1">
					<img src="/images/ci/ng/small_previous.gif" alt="Previous Page">
				</a>
				Page {{course.page}} of {{course.numPages}} 
				<a class="pagelink" title="Next Page" href="" ng-click="getPage(course, course.page + 1);" ng-hide="course.page == course.numPages">
					<img src="/images/ci/ng/small_next.gif" alt="Next Page">
				</a>
				<a class="pagelink" title="Last Page" href="" ng-click="getPage(course, course.numPages);" ng-hide="course.page == course.numPages">
					<img src="/images/ci/ng/small_ffwd.gif" alt="Last Page">
				</a>
			</li>
		</ul>
	</div>
	<p class='update'>Updated: {{lastupdate}}</p>
</div>
</div>

<!-- Style at bottom or else IE8 won't apply it -->
<style type="text/css">
/* add a border to each element in the ul */
#ubc_ctlt_ca_angular_div li
{
	border-top: 1px dotted #ccc;
	overflow: hidden;
	width: 100%;
}
#ubc_ctlt_ca_angular_div li:first-child
{
	border-top: none;
}
/* spacing */
#ubc_ctlt_ca_angular_div li a
{
	padding: 0.5em 0;
}
#ubc_ctlt_ca_angular_div h4
{
	padding-top: 0.5em;
}
/* paging */
#ubc_ctlt_ca_angular_div li.rumble
{
	background: none;
}
#ubc_ctlt_ca_angular_div li.inventory_paging
{
	border-top: none;
}
#ubc_ctlt_ca_angular_div li.inventory_paging a
{
	padding: 0;
}
/* push link and (view) links to opposite corners of the column*/
#ubc_ctlt_ca_angular_div a.main-link
{
	float: left;
	word-wrap: break-word;
}
#ubc_ctlt_ca_angular_div a.side-controls
{
	font-size: 0.85em;
	font-style: italic;
	float: right;
}
/* highlight on hover */
#ubc_ctlt_ca_angular_div li a:hover
{
	background: #ececec;
	text-decoration: none;
}
/* make the file count visually different from course name */
#ubc_ctlt_ca_angular_div h4 a span
{
	color: #555;
}
/* make the date updated text small */
#ubc_ctlt_ca_angular_div p.update
{
	font-size: 0.85em;
	margin-top: 0.75em;
}
/* make sure to hide the file lists on load */
#ubc_ctlt_ca_angular_div div.hideInitially
{
	display: none;
}
</style>


<bbNG:jsBlock>
<script type="text/javascript">

function FileListCtrl($scope, Files, Status, CourseFiles) 
{
	$scope.courseFiles = Files.get(
		function(val)
		{ // show the alerts if the user actually has untagged files
			// workaround for checking if associative array not empty due to IE8
			// not supporting the easier methods
			var count = 0;
			for (course in val.courses)
			{
				count++;
				break;
			}

			if (count > 0)
			{ 
				$('ubc_ctlt_ca_app').removeClassName('hideInitially');
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
	$scope.getPage = function(course, desiredPage)
	{
		if (desiredPage < 1 || 
			desiredPage > course.numPages ||
			desiredPage == course.page)
		{ // invalid page request, ignore
			return;
		}
		CourseFiles.get(
			{courseid: course.courseId, page: desiredPage},
			function(c) 
			{
				c.show = true;
				$scope.courseFiles.courses[c.courseId] = c;
			}
		);
	};
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
	services.factory('CourseFiles', 
		function($resource)
		{
			return $resource('/webapps/ubc-copyright-alerts-BBLEARN/alertsmodule/files/:courseid/:page');
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
	"url": "//ajax.googleapis.com/ajax/libs/angularjs/1.2.0-rc.2/angular.js"
	},
	{
	"loaded": function() { 
			try { angular.module("ngResource"); } 
			catch(e) { return false; } 
			return true; 
		}, 
	"url": "//ajax.googleapis.com/ajax/libs/angularjs/1.2.0-rc.2/angular-resource.js",
	"after": partial(startAngular)
	}
]);
	
</script>
</bbNG:jsBlock>
</bbNG:includedPage>
