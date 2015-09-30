LUPAPISTE.ApplicationsForemanSearchFiltersListModel = function(params) {
  "use strict";
  var self = this;

  var dataProvider = params.dataProvider || {};

  ko.utils.extend(self, new LUPAPISTE.ApplicationsSearchFiltersListModel(params));

  self.savedFilters = lupapisteApp.services.applicationFiltersService.savedForemanFilters;

  // TODO override save
  self.saveFilter = function() {
    var title = self.newFilterName();

    var filter = {
      handlers:      _.map(ko.unwrap(lupapisteApp.services.handlerFilterService.selected), "id"), //util.getIn(self.dataProvider, ["handler", "id"]),
      tags:          _.map(ko.unwrap(lupapisteApp.services.tagFilterService.selected), "id"),
      operations:    ["tyonjohtajan-nimeaminen-v2"],
      organizations: _.map(ko.unwrap(lupapisteApp.services.organizationFilterService.selected), "id"),
      areas:         _.map(ko.unwrap(lupapisteApp.services.areaFilterService.selected), "id")
    };

    ajax
    .command("save-application-filter", {title: title, filter: filter, sort: ko.toJS(dataProvider.sort), filterType: "foreman"})
    .error(util.showSavedIndicator)
    .success(function(res) {
      util.showSavedIndicator(res);
      lupapisteApp.services.applicationFiltersService.addForemanFilter(res.filter);
      self.newFilterName("");
      self.showSavedFilters(true);
    })
    .call();
  };

  // clear filters as well?
  self.clearFilters = function() {
    lupapisteApp.services.handlerFilterService.selected([]);
    lupapisteApp.services.tagFilterService.selected([]);
    lupapisteApp.services.operationFilterService.selected([]);
    lupapisteApp.services.organizationFilterService.selected([]);
    lupapisteApp.services.areaFilterService.selected([]);
    lupapisteApp.services.applicationFiltersService.selected(undefined);
    dataProvider.searchField("");
    self.newFilterName("");
  };

};
