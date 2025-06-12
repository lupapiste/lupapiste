LUPAPISTE.OperationFilterService = function(applicationFiltersService) {
  "use strict";
  var self = this;

  var _data = ko.observable();

    _data.subscribe( function ()  {
      hub.send( "operationFilterService::changed", {} );
  });


  self.data = ko.pureComputed(function() {
    return _data();
  });

  self.selected = ko.observableArray([]);

  var savedFilter = ko.pureComputed(function() {
    return util.getIn(applicationFiltersService.selected(), ["filter", "operations"]);
  });

  function wrapInObject(operations) {
    return _.map(operations, function(op) {
      return {label: loc("operations." + op),
              id: op};
    });
  }

  ko.computed(function() {
    self.selected([]);
    ko.utils.arrayPushAll(self.selected, savedFilter() ? wrapInObject(savedFilter()) : []);
  });

  function load() {
    if (lupapisteApp.models.globalAuthModel.ok("get-application-operations")) {
      ajax.query("get-application-operations")
        .success(function(res) {
        _data(res.operationsByPermitType);
        })
        .call();
      return true;
    }
    return false;
  }

  if (!load()) {
    hub.subscribe("global-auth-model-loaded", load, true);
  }

};
