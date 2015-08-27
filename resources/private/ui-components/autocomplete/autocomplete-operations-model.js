LUPAPISTE.AutocompleteOperationsModel = function(params) {
  "use strict";
  var self = this;

  self.selected = params.selectedValues;
  self.query = ko.observable("");

  var operationsByPermitType = ko.observable();

  var defaultFilter = ko.pureComputed(function() {
    var applicationFilters = lupapisteApp.models.currentUser.applicationFilters;
    return applicationFilters && 
           applicationFilters()[0].filter.operations &&
           applicationFilters()[0].filter.operations() ||
           [];
  });

  function wrapInObject(operations) {
    return _.map(operations, function(op) {
      return {label: loc("operations." + op),
              id: op};
    });
  }

  ajax
    .query("get-application-operations")
    .error(_.noop)
    .success(function(res) {
      operationsByPermitType(res.operationsByPermitType);

      ko.utils.arrayPushAll(self.selected, wrapInObject(defaultFilter()));
    })
    .call();

  self.data = ko.pureComputed(function() {
    var result = [];

    var data = _.map(operationsByPermitType(), function(operations, permitType) {
      return {
        permitType: loc(permitType),
        operations: wrapInObject(operations)
      };
    });

    _.forEach(data, function(item) {
      var header = {label: item.permitType, groupHeader: true};

      var filteredData = util.filterDataByQuery(item.operations, self.query() || "", self.selected());

      // append group header and group items to result data
      if (filteredData.length > 0) {
        result = result.concat(header).concat(filteredData);
      }
    });

    return result;
  });
};
