LUPAPISTE.ApplicationsSearchFiltersListModel = function(params) {
  "use strict";
  var self = this;

  var dataProvider = params.dataProvider || {};

  self.showSavedFilters = ko.observable(false);

  self.newFilterName = ko.observable();

  self.savedFilters = lupapisteApp.services.applicationFiltersService.savedFilters;

  self.saveFilter = function() {
    var title = self.newFilterName();

    var filter = {
      handlers:      _.map(ko.unwrap(lupapisteApp.services.handlerFilterService.selected), "id"), //util.getIn(self.dataProvider, ["handler", "id"]),
      tags:          _.map(ko.unwrap(lupapisteApp.services.tagFilterService.selected), "id"),
      operations:    _.map(ko.unwrap(lupapisteApp.services.operationFilterService.selected), "id"),
      organizations: _.map(ko.unwrap(lupapisteApp.services.organizationFilterService.selected), "id"),
      areas:         _.map(ko.unwrap(lupapisteApp.services.areaFilterService.selected), "id")
    };

    ajax
    .command("save-application-filter", {title: title, filter: filter, sort: ko.toJS(dataProvider.sort)})
    .error(util.showSavedIndicator)
    .success(function(res) {
      util.showSavedIndicator(res);
      lupapisteApp.services.applicationFiltersService.addFilter(res.filter);
      self.newFilterName("");
      self.showSavedFilters(true);
    })
    .call();
  };

};
