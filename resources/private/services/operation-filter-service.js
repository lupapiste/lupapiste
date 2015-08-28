LUPAPISTE.OperationFilterService = function(params) {
  "use strict";
  var self = this;

  self.data = ko.observable();

  self.selected = ko.observableArray([]);

  var defaultFilter = ko.pureComputed(function() {
    var applicationFilters = _.first(lupapisteApp.models.currentUser.applicationFilters());
    return applicationFilters &&
           applicationFilters.filter.operations &&
           applicationFilters.filter.operations() ||
           [];
  });

  function wrapInObject(operations) {
    return _.map(operations, function(op) {
      return {label: loc("operations." + op),
              id: op};
    });
  }

  ko.computed(function() {
    ko.utils.arrayPushAll(self.selected, wrapInObject(defaultFilter()));
  });

  ajax
    .query("get-application-operations")
    .error(_.noop)
    .success(function(res) {
      self.data(res.operationsByPermitType);
    })
    .call();

};
