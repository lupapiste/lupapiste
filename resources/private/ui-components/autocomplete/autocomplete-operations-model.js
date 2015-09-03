LUPAPISTE.AutocompleteOperationsModel = function() {
  "use strict";
  var self = this;

  self.selected = lupapisteApp.services.operationFilterService.selected;

  self.query = ko.observable("");

  function wrapInObject(operations) {
    return _.map(operations, function(op) {
      return {label: loc("operations." + op),
              id: op};
    });
  }

  self.data = ko.pureComputed(function() {
    var result = [];

    var data = _.map(lupapisteApp.services.operationFilterService.data(), function(operations, permitType) {
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
        result = result.concat(header).concat(_.sortBy(filteredData, "label"));
      }
    });

    return result;
  });
};
