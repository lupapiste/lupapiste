LUPAPISTE.OperationFilterService = function(applicationFiltersService) {
  "use strict";
  var self = this;

  var _data = ko.observable();

  self.data = ko.pureComputed(function() {
    return _data();
  });

  self.selected = ko.observableArray([]);

  var defaultFilter = ko.pureComputed(function() {
    var applicationFilters = _.find(applicationFiltersService.savedFilters(), function(f) {
      return f.isDefaultFilter();
    });
    return util.getIn(applicationFilters, ["filter", "operations"]) || [];
  });

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
    ko.utils.arrayPushAll(self.selected, savedFilter() ? wrapInObject(savedFilter()) : wrapInObject(defaultFilter()));
  });

  ajax
    .query("get-application-operations")
    .error(_.noop)
    .success(function(res) {
      _data(res.operationsByPermitType);
    })
    .call();
};
