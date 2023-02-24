LUPAPISTE.ApplicationsCompanySearchFiltersListModel = function(params) {
  "use strict";
  var self = this;

  var dataProvider = params.dataProvider || {};

  ko.utils.extend(self, new LUPAPISTE.ApplicationsSearchFiltersListModel(params));

  self.savedFilters = lupapisteApp.services.applicationFiltersService.savedCompanyFilters;

  self.saveFilter = function() {
    var title = self.newFilterName();

    var filter = {
      companyTags:   _.map(ko.unwrap(lupapisteApp.services.companyTagFilterService.selected), "id"),
      operations:    _.map(ko.unwrap(lupapisteApp.services.operationFilterService.selected), "id")
    };

    ajax
    .command("save-application-filter", {title: title, filter: filter, sort: ko.toJS(dataProvider.sort), filterType: "company"})
    .error(util.showSavedIndicator)
    .success(function(res) {
      util.showSavedIndicator(res);
      lupapisteApp.services.applicationFiltersService.addCompanyFilter(res.filter);
      self.newFilterName("");
      self.showSavedFilters(true);
    })
    .call();
  };
};
