var Services = angular.module('SystemConfigServices', ['ngResource']);
Services.factory('Schedule', 
	function($resource) 
	{
		return $resource('systemconfig/schedule');
	}
);

Services.factory('Status', 
	function($resource) 
	{
		return $resource(
				'systemconfig/status/:action', 
				{action: 'status'},
				{
					stop: {method: 'GET', params: {action: 'stop'}}
				}
				);
	}
);

Services.factory('MetadataAttributes', 
	function($resource) 
	{
		return $resource('systemconfig/metadata');
	}
);

Services.factory('Host', 
	function($resource) 
	{
		return $resource('systemconfig/host');
	}
);

var SystemConfigModule = angular.module('SystemConfigModule', ['SystemConfigServices']);

function ScheduleCtrl($scope, $timeout, Schedule, Host) 
{
	$scope.loading = true;
	$scope.saving = "";
	// the schedule get callback is a hack to get the jqCron plugin to update once the saved data is loaded
	$scope.schedule = Schedule.get(function (data) { jQuery('#croninput').val(data.cron); jQuery('#croninput').blur(); $scope.loading = false; });
	$scope.host = Host.get();
	
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

	$scope.saveSchedule = function() 
	{
		$scope.saving = "saving";
		$scope.schedule.$save($scope.saveSuccessful, $scope.saveError);
		$scope.host.$save(function() {},$scope.saveError);
	};
}

function StatusCtrl($scope, $timeout, Status)
{
	(function updateStatus() {
		$scope.status = Status.get(function() { $timeout(updateStatus, 10000); });
	})();

	$scope.stop = function()
	{
		Status.stop();
	};
}

function MetadataIdCtrl($scope, MetadataAttributes)
{
	$scope.config = MetadataAttributes.get();

	$scope.remove = function (attr) 
	{
		var i = $scope.config.attributes.indexOf(attr);
		$scope.config.attributes.splice(i, 1);
		$scope.config.$save();
	};
	
	$scope.submit = function() 
	{
		if (this.newattr) 
		{
			$scope.config.attributes.push(this.newattr);
			$scope.config.$save();
			this.newattr = '';
		}
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