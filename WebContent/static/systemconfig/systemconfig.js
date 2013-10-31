var Services = angular.module('SystemConfigServices', ['ngResource']);
Services.factory('Schedule', 
	function($resource) 
	{
		return $resource('/webapps/ubc-copyright-alerts-BBLEARN/systemconfig/schedule');
	}
);

Services.factory('Status', 
	function($resource) 
	{
		return $resource(
				'/webapps/ubc-copyright-alerts-BBLEARN/systemconfig/status/:action', 
				{action: 'status'},
				{
					stop: {method: 'GET', params: {action: 'stop'}},
					progress: {method: 'GET', params: {action: 'progress'}}
				}
				);
	}
);

Services.factory('MetadataAttributes', 
	function($resource) 
	{
		return $resource('/webapps/ubc-copyright-alerts-BBLEARN/systemconfig/metadata');
	}
);

Services.factory('Host', 
	function($resource) 
	{
		return $resource('/webapps/ubc-copyright-alerts-BBLEARN/systemconfig/host');
	}
);

var SystemConfigModule = angular.module('SystemConfigModule', ['SystemConfigServices']);

function ScheduleCtrl($scope, $timeout, $http, Schedule, Host) 
{
	$scope.loading = true;
	$scope.saving = "";
	// the schedule get callback is a hack to get the jqCron plugin to update once the saved data is loaded
	$scope.schedule = Schedule.get(function (data) { jQuery('#croninput').val(data.cron); jQuery('#croninput').blur(); $scope.loading = false; });
	$scope.host = Host.get();
	
	// callback for saving schedule successful
	$scope.saveSuccessful = function(ret)
	{
		if ($scope.saving == "error") return;
		
		$timeout(function() { $scope.saving = "success"; }, 500);
		$timeout(function() { $scope.saving = ""; }, 5000);
	};
	
	// callback for saving schedule failed
	$scope.saveError = function()
	{
		$scope.saving = "error";
	};

	// save the current schedule to the database
	$scope.saveSchedule = function() 
	{
		$scope.saving = "saving";
		$scope.schedule.$save($scope.saveSuccessful, $scope.saveError);
		$scope.host.$save(function() {},$scope.saveError);
	};
}

function StatusCtrl($scope, $timeout, Status, Host)
{
	$scope.updateStatus = function()
	{
		Status.get(
			function(ret)
			{
				if ($scope.status === undefined || ret.status != $scope.status.status)
				{ // prevent flickering by only updating if there's a change
					$scope.status = ret;
				}
			}
		);
	};
	
	// poll for a new status update every 10 seconds
	(function pollStatus() {
		$scope.updateStatus();
		$timeout(pollStatus, 10000);
	})();
	
	$scope.host = Host.get();

	$scope.stop = function()
	{
		Status.stop();
	};
	
	$scope.getProgress = function()
	{
		$scope.progress = Status.progress();
	};
	
	$scope.getProgress();
}

function MetadataIdCtrl($scope, $timeout, MetadataAttributes)
{
	$scope.config = MetadataAttributes.get();
	$scope.saving = "";

	$scope.remove = function (attr) 
	{
		var i = $scope.config.attributes.indexOf(attr);
		$scope.config.attributes.splice(i, 1);
		$scope.config.$save();
	};
	
	$scope.saveSuccessful = function()
	{
		if ($scope.saving == "error") return;
		$timeout(function() { $scope.saving = "success"; }, 500);
		$timeout(function() { $scope.saving = ""; }, 6000);
	};
	
	$scope.saveError = function()
	{
		$scope.saving = "error";
	};
	
	
	$scope.submit = function() 
	{
		$scope.saving = "saving";
		$scope.config.$save($scope.saveSuccessful, $scope.saveError);
		return false;
	};
}

// Convert the jqCron jQuery plugin into an angularjs directive
SystemConfigModule.directive('jqcronui',
	function() 
	{
		return function postLink(scope, element, attrs, ctrl) 
		{
			$(element).jqCron(
				{
				    enabled_minute: true,
				    multiple_dom: true,
				    multiple_month: true,
				    multiple_mins: true,
				    multiple_dow: true,
				    multiple_time_hours: true,
				    multiple_time_minutes: false,
				    no_reset_button: false,
				    default_value: scope.schedule.cron,
				    bind_method: {
				    	set: function($element, value) {
				    		if(!scope.$$phase) 
				    		{ // need to make sure we're not in apply as the initialisation will have an initial call to apply
					    		scope.$apply( function() { scope.schedule.cron = value;} );
				    		}
				    	}
				    },
				    lang: 'en'
				}
			);
		}
	}
);


// data validation, make sure we're getting an integer
var INTEGER_REGEXP = /^\-?\d*$/;
SystemConfigModule.directive('integer',
	function()
	{
		return {
			link: function(scope, elm, attrs, ctrl)
			{
				ctrl.$parsers.unshift(function(viewValue)
				{
					if (INTEGER_REGEXP.test(viewValue))
					{
						// it is valid
						ctrl.$setValidity('integer', true);
						return viewValue;
					}
					else
					{
						// it is invalid, return undefined (no model update)
						ctrl.$setValidity('integer', false);
						return undefined;
					}
				});
			},
			require: "ngModel"
		};
	}
);
