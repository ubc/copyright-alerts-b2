<%@ page contentType="text/html; charset=UTF-8" %>
<%@ taglib prefix="bbNG" uri="/bbNG"%>

<bbNG:includedPage ctxId="ctx">
<div id="ubc_ctlt_ca_insert_div"></div>
<div id="ubc_ctlt_ca_angular_div">
	<div ng-controller="FileListCtrl">
		<p>Hello World!</p>
		{{files.Test}}
	</div>
</div>
<script type="text/javascript">

function FileListCtrl($scope, Files) 
{
	$scope.files = Files.get(); 
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
</bbNG:includedPage>
