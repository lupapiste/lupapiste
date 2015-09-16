LUPAPISTE.ApplicationsSearchFiltersListModel = function(params) {
  "use strict";
  var self = this;

  var dataProvider = params.dataProvider || {};

  self.showSavedFilters = ko.observable(false);
  self.newFilterName = ko.observable();

  self.savedFilters = lupapisteApp.models.currentUser.applicationFilters;

  self.saveFilter = function() {
    var filter = {
      handlers:      _.map(ko.unwrap(lupapisteApp.services.handlerFilterService.selected), "id"), //util.getIn(self.dataProvider, ["handler", "id"]),
      tags:          _.map(ko.unwrap(lupapisteApp.services.tagFilterService.selected), "id"),
      operations:    _.map(ko.unwrap(lupapisteApp.services.operationFilterService.selected), "id"),
      organizations: _.map(ko.unwrap(lupapisteApp.services.organizationFilterService.selected), "id"),
      areas:         _.map(ko.unwrap(lupapisteApp.services.areaFilterService.selected), "id")
    };

    ajax
    .command("save-application-filter", {title: self.newFilterName(), filter: filter, sort: ko.toJS(dataProvider.sort), "filter-id": "69"})
    .error(util.showSavedIndicator)
    .success(util.showSavedIndicator)
    .call();
  };

};
