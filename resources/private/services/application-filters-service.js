LUPAPISTE.ApplicationFiltersService = function() {
  "use strict";
  var self = this;

  var _savedFilters = ko.observableArray([]);

  self.selected = ko.observable();

  self.savedFilters = ko.computed(function() {
    return _savedFilters();
  });

  function wrapFilter(filter) {
    filter.edit = ko.observable(false);
    filter.removeFilter = function(filter) {
      ajax
      .command("remove-application-filter", {"filter-id": filter.id()})
      .error(util.showSavedIndicator)
      .success(function() {
        _savedFilters.remove(function(f) {
          return ko.unwrap(f.id) === ko.unwrap(filter.id);
        });
      })
      .call();
    };
    filter.defaultFilter = function(filter) {
      ajax
      .command("update-default-application-filter", {"filter-id": filter.id()})
      .error(util.showSavedIndicator)
      .success(function() {
        _.forEach(_savedFilters(), function(f) {
          f.isDefaultFilter(false);
        });
        filter.isDefaultFilter(true);
      })
      .call();
    };
    filter.isDefaultFilter = ko.observable(filter.id() === util.getIn(lupapisteApp.models.currentUser, ["defaultFilter", "id"]));
    return filter;
  }

  ko.computed(function() {
    _savedFilters(_(lupapisteApp.models.currentUser.applicationFilters())
      .map(wrapFilter)
      .reverse()
      .value());
  });

  self.addFilter = function(filter) {
    _savedFilters.unshift(wrapFilter(ko.mapping.fromJS(filter)));
  };
};
