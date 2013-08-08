var Services = angular.module('ModuleServices', ['ngResource']);
Services.factory('Files', 
	function($resource) 
	{
		return $resource('systemconfig/schedule');
	}
);

var SystemConfigModule = angular.module('AlertsModule', ['ModuleServices']);

function FileListCtrl($scope, Files)
{
	console.log("Log");
	$scope.files = Files.get();
}