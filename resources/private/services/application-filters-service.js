LUPAPISTE.ApplicationFiltersService = function() {
  "use strict";
  var self = this;

  var _savedFilters = ko.observableArray([]);

  self.selected = ko.observable();

  self.selected.subscribe(function(val) {
    _.forEach(_savedFilters(), function(f) {
      f.isSelected(false);
    });
    // val is not defined when selection is cleared
    if (val) {
      val.isSelected(true);
    }
  });

  self.savedFilters = ko.computed(function() {
    return _savedFilters();
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

  function wrapFilter(filter) {
    filter.edit = ko.observable(false);
    filter.isSelected = ko.observable();
    filter.isDefaultFilter = ko.observable(filter.id() === util.getIn(lupapisteApp.models.currentUser, ["defaultFilter", "id"]));
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
      // unset old or set new default filter
      var id = filter.isDefaultFilter() ? null : filter.id();
      ajax
      .command("update-default-application-filter", {"filter-id": id})
      .error(util.showSavedIndicator)
      .success(function() {
        _.forEach(_savedFilters(), function(f) {
          f.isDefaultFilter(false);
        });
        if (id) {
          filter.isDefaultFilter(true);
        }
      })
      .call();
    };
    filter.selectFilter = function(filter) {
      _.forEach(_savedFilters(), function(f) {
        f.isSelected(false);
      });
      filter.isSelected(true);
      self.selected(filter);
    };
    return filter;
  }

  ko.computed(function() {
    _savedFilters(_(lupapisteApp.models.currentUser.applicationFilters())
      .map(wrapFilter)
      .reverse()
      .value());
  });

  self.addFilter = function(filter) {
    _savedFilters.remove(function(f) {
      return ko.unwrap(f.id) === ko.unwrap(filter.id);
    });
    var wrapped = wrapFilter(ko.mapping.fromJS(filter));
    _savedFilters.unshift(wrapped);
    self.selected(wrapped);
  };
};
