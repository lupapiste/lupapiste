LUPAPISTE.ApplicationFiltersService = function() {
  "use strict";
  var self = this;

  var _savedFilters = ko.observableArray([]);

  var _savedForemanFilters = ko.observableArray([]);

  self.selected = ko.observable();

  self.savedFilters = ko.pureComputed(function() {
    return _savedFilters();
  });

  self.savedForemanFilters = ko.pureComputed(function() {
    return _savedForemanFilters();
  });

  self.defaultFilter = ko.pureComputed(function() {
    return _.find(_savedFilters(), function(f){
      return f.isDefaultFilter();
    });
  });

  self.defaultFilter.subscribe(function(val) {
    if (!self.selected()) {
      self.selected(val);
    }
  });

  function wrapFilter(filterType) {
    return function(filter) {
      filter.edit = ko.observable(false);
      filter.isDefaultFilter = ko.pureComputed(function () {return filter.id() === util.getIn(lupapisteApp.models.currentUser, ["defaultFilter", "id"]);});
      filter.removeFilter = function(filter) {
        ajax
        .command("remove-application-filter", {filterId: filter.id(), filterType: filterType})
        .error(util.showSavedIndicator)
        .success(function() {
          var filters = lupapisteApp.models.currentUser[filterType + "Filters"];
          filters.remove(function(f) {
            return ko.unwrap(f.id) === ko.unwrap(filter.id);
          });
          if (util.getIn(self.selected(), ["id"]) === ko.unwrap(filter.id)) {
            self.selected(null);
          }
        })
        .call();
      };
      filter.defaultFilter = function(filter) {
        // unset old or set new default filter
        var id = filter.isDefaultFilter() ? null : filter.id();
        ajax
        .command("update-default-application-filter", {filterId: id, filterType: filterType})
        .error(util.showSavedIndicator)
        .success(function() {
          lupapisteApp.models.currentUser.defaultFilter.id(id);
        })
        .call();
      };
      return filter;
    };
  }

  ko.computed(function() {
    _savedFilters(_(lupapisteApp.models.currentUser.applicationFilters())
      .map(wrapFilter("application"))
      .reverse()
      .value());
  });

  ko.computed(function() {
    _savedForemanFilters(_(lupapisteApp.models.currentUser.foremanFilters())
      .map(wrapFilter("foreman"))
      .reverse()
      .value());
  });

  self.addFilter = function(filter) {
    _savedFilters.remove(function(f) {
      return ko.unwrap(f.id) === ko.unwrap(filter.id);
    });
    if (_.isEmpty(_savedFilters())) {
      lupapisteApp.models.currentUser.defaultFilter.id(ko.unwrap(filter.id));
    }
    var wrapped = wrapFilter("application")(ko.mapping.fromJS(filter));
    lupapisteApp.models.currentUser.applicationFilters.push(wrapped);
    self.selected(wrapped);
  };

  self.addForemanFilter = function(filter) {
    _savedForemanFilters.remove(function(f) {
      return ko.unwrap(f.id) === ko.unwrap(filter.id);
    });
    if (_.isEmpty(_savedForemanFilters())) {
      lupapisteApp.models.currentUser.defaultFilter.foremanFilterId(ko.unwrap(filter.id));
    }
    var wrapped = wrapFilter("foreman")(ko.mapping.fromJS(filter));
    lupapisteApp.models.currentUser.foremanFilters.push(wrapped);
    self.selected(wrapped);
  };
};
