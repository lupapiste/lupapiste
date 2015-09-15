LUPAPISTE.ApplicationsSearchFiltersListModel = function(params) {
  "use strict";
  var self = this;

  self.showSavedFilters = ko.observable(false);
  self.newFilterName = ko.observable();

  self.saveFilter = function() {
    console.log("save");
  };
};
