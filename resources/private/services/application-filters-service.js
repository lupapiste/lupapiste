LUPAPISTE.ApplicationFiltersService = function() {
  "use strict";
  var self = this;

  var _savedFilters = ko.observableArray([]);

  var _savedForemanFilters = ko.observableArray([]);

  var _savedCompanyFilters = ko.observableArray([]);

  self.selected = ko.observable();

  self.savedFilters = ko.pureComputed(function() {
    return _savedFilters();
  });

  self.savedForemanFilters = ko.pureComputed(function() {
    return _savedForemanFilters();
  });

  self.savedCompanyFilters = ko.pureComputed(function() {
    return _savedCompanyFilters();
  });

  self.defaultFilter = ko.pureComputed(function() {
    return _.find(_savedFilters(), function(f){
      return f.isDefaultFilter();
    });
  });

  self.defaultCompanyFilter = ko.pureComputed(function() {
    return _.find(_savedCompanyFilters(), function(f){
      return f.isDefaultFilter();
    });
  });

  self.defaultFilter.subscribe(function(val) {
    if (!self.selected()) {
      self.selected(val);
    }
  });

  self.defaultCompanyFilter.subscribe(function(val) {
    if (!self.selected()) {
      self.selected(val);
    }
  });

  function removeFilter(filters, filter) {
    ajax
      .command("remove-application-filter", {filterId: filter.id(), filterType: filter.filterType})
      .error(util.showSavedIndicator)
      .success(function() {
        filters.remove(function(f) {
          return ko.unwrap(f.id) === ko.unwrap(filter.id);
        });
        if (util.getIn(self.selected(), ["id"]) === ko.unwrap(filter.id)) {
          self.selected(null);
        }
      })
      .call();
  }

  function updateDefaultFilter(defaultFilterId, filter) {
      var id = filter.isDefaultFilter() ? null : filter.id();
      ajax
        .command("update-default-application-filter", {filterId: id, filterType: filter.filterType})
        .error(util.showSavedIndicator)
        .success(function() {
          // unset old or set new default filter
          defaultFilterId(id);
        })
        .call();
  }

  function wrapForemanFilter(filter) {
    filter.filterType = "foreman";
    filter.edit = ko.observable(false);
    filter.isDefaultFilter = ko.pureComputed(function () {
      return filter.id() === util.getIn(lupapisteApp.models.currentUser, ["defaultFilter", "foremanFilterId"]);
    });
    filter.removeFilter = _.partial(removeFilter, lupapisteApp.models.currentUser.foremanFilters);
    filter.defaultFilter = _.partial(updateDefaultFilter, lupapisteApp.models.currentUser.defaultFilter.foremanFilterId);
    return filter;
  }

  function wrapCompanyFilter(filter) {
    filter.filterType = "company";
    filter.edit = ko.observable(false);
    filter.isDefaultFilter = ko.pureComputed(function () {
      return filter.id() === util.getIn(lupapisteApp.models.currentUser, ["defaultFilter", "companyFilterId"]);
    });
    filter.removeFilter = _.partial(removeFilter, lupapisteApp.models.currentUser.companyApplicationFilters);
    filter.defaultFilter = _.partial(updateDefaultFilter, lupapisteApp.models.currentUser.defaultFilter.companyFilterId);
    return filter;
  }

  function wrapFilter(filter) {
    filter.filterType = "application";
    filter.edit = ko.observable(false);
    filter.isDefaultFilter = ko.pureComputed(function () {
      return filter.id() === util.getIn(lupapisteApp.models.currentUser, ["defaultFilter", "id"]);
    });
    filter.removeFilter = _.partial(removeFilter, lupapisteApp.models.currentUser.applicationFilters);
    filter.defaultFilter = _.partial(updateDefaultFilter, lupapisteApp.models.currentUser.defaultFilter.id);
    return filter;
  }

  ko.computed(function() {
    _savedFilters(_(lupapisteApp.models.currentUser.applicationFilters())
      .map(wrapFilter)
      .reverse()
      .value());
  });

  ko.computed(function() {
    _savedForemanFilters(_(lupapisteApp.models.currentUser.foremanFilters())
      .map(wrapForemanFilter)
      .reverse()
      .value());
  });

  ko.computed(function() {
    _savedCompanyFilters(_(lupapisteApp.models.currentUser.companyApplicationFilters())
      .map(wrapCompanyFilter)
      .reverse()
      .value());
  });

  self.reloadDefaultFilter = function() {
    var filter = _.find(_savedFilters(), function(f){
      return f.isDefaultFilter();
    });
    self.selected(filter);
  };

  self.reloadDefaultForemanFilter = function() {
    var filter = _.find(_savedForemanFilters(), function(f){
      return f.isDefaultFilter();
    });
    self.selected(filter);
  };

  self.reloadDefaultCompanyFilter = function() {
    var filter = _.find(_savedCompanyFilters(), function(f){
      return f.isDefaultFilter();
    });
    self.selected(filter);
  };

  self.addFilter = function(filter) {
    _savedFilters.remove(function(f) {
      return ko.unwrap(f.id) === ko.unwrap(filter.id);
    });
    if (_.isEmpty(_savedFilters())) {
      lupapisteApp.models.currentUser.defaultFilter.id(ko.unwrap(filter.id));
    }
    var wrapped = wrapFilter(ko.mapping.fromJS(filter));
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
    var wrapped = wrapForemanFilter(ko.mapping.fromJS(filter));
    lupapisteApp.models.currentUser.foremanFilters.push(wrapped);
    self.selected(wrapped);
  };

  self.addCompanyFilter = function(filter) {
    _savedForemanFilters.remove(function(f) {
      return ko.unwrap(f.id) === ko.unwrap(filter.id);
    });
    if (_.isEmpty(_savedCompanyFilters())) {
      lupapisteApp.models.currentUser.defaultFilter.companyFilterId(ko.unwrap(filter.id));
    }
    var wrapped = wrapCompanyFilter(ko.mapping.fromJS(filter));
    lupapisteApp.models.currentUser.companyApplicationFilters.push(wrapped);
    self.selected(wrapped);
  };
};
