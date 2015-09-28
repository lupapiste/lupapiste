LUPAPISTE.OperationFilterService = function(applicationFiltersService) {
  "use strict";
  var self = this;

  var _data = ko.observable();

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

  hub.subscribe("global-auth-model-loaded", function(){
    if (lupapisteApp.models.globalAuthModel.ok("get-application-operations")) {
      ajax.query("get-application-operations")
        .success(function(res) {
        _data(res.operationsByPermitType);
        })
        .call();
    }
  }, true);

};
