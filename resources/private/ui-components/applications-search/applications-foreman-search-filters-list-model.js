LUPAPISTE.ApplicationsForemanSearchFiltersListModel = function(params) {
  "use strict";
  var self = this;

  var dataProvider = params.dataProvider || {};

  ko.utils.extend(self, new LUPAPISTE.ApplicationsSearchFiltersListModel(params));

  self.savedFilters = lupapisteApp.services.applicationFiltersService.savedForemanFilters;

  self.saveFilter = function() {
    var title = self.newFilterName();

    var filter = {
      handlers:      _.map(ko.unwrap(lupapisteApp.services.handlerFilterService.selected), "id"), //util.getIn(self.dataProvider, ["handler", "id"]),
      tags:          _.map(ko.unwrap(lupapisteApp.services.tagFilterService.selected), "id"),
      operations:    ["tyonjohtajan-nimeaminen-v2", "tyonjohtajan-nimeaminen"],
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
};
