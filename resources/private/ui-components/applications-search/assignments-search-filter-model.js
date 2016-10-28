LUPAPISTE.AssignmentsSearchFilterModel = function(params) {
  "use strict";
  var self = this;

  self.dataProvider = params.dataProvider;
  self.gotResults = params.gotResults;

  self.searchFieldSelected = ko.observable(false);
};
