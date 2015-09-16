LUPAPISTE.ApplicationsSearchFiltersListModel = function(params) {
  "use strict";
  var self = this;

  var dataProvider = params.dataProvider || {};

  self.showSavedFilters = ko.observable(false);
  self.newFilterName = ko.observable();

  self.savedFilters = ko.pureComputed(function() {
    return _.map(lupapisteApp.models.currentUser.applicationFilters(), function (filter) {
      filter.edit = ko.observable(false);
      filter.removeFilter = function(filter) {
        ajax
        .command("remove-application-filter", {"filter-id": filter.id()})
        .error(util.showSavedIndicator)
        .success(function() {
          hub.send("reload-current-user");
        })
        .call();
      };
      filter.defaultFilter = function(filter) {
        ajax
        .command("update-default-application-filter", {"filter-id": filter.id()})
        .error(util.showSavedIndicator)
        .success(function() {
          hub.send("reload-current-user");
        })
        .call();
      };
      filter.isDefaultFilter = filter.id() === util.getIn(lupapisteApp.models.currentUser, ["defaultFilter", "id"]);
      return filter;
    });
  });

  self.saveFilter = function() {
    var title = self.newFilterName();

    var filter = {
      handlers:      _.map(ko.unwrap(lupapisteApp.services.handlerFilterService.selected), "id"), //util.getIn(self.dataProvider, ["handler", "id"]),
      tags:          _.map(ko.unwrap(lupapisteApp.services.tagFilterService.selected), "id"),
      operations:    _.map(ko.unwrap(lupapisteApp.services.operationFilterService.selected), "id"),
      organizations: _.map(ko.unwrap(lupapisteApp.services.organizationFilterService.selected), "id"),
      areas:         _.map(ko.unwrap(lupapisteApp.services.areaFilterService.selected), "id")
    };

    // TODO filter-id from selected filter
    ajax
    .command("save-application-filter", {title: title, filter: filter, sort: ko.toJS(dataProvider.sort), "filter-id": filter.id})
    .error(util.showSavedIndicator)
    .success(function(res) {
      util.showSavedIndicator(res);
      hub.send("reload-current-user");
    })
    .call();
  };

};
