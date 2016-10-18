LUPAPISTE.AttachmentsFilterSetModel = function(filters) {
  "use strict";
  var self = this;
  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel());

  var service = lupapisteApp.services.attachmentsService;

  var groupedFilters = ko.observableArray();
  var filtersByTag = {};
  var subscriptions = [];

  // Attachments ids which should not be filtered out.
  var forceVisibleIds = ko.observableArray();

  // All filter object grouped. Logical rules are applied as follows
  // [[a || b] && [c || d]], where a, b, c and d are values of filter 'active' field.
  self.filters = self.disposedPureComputed(function() {
    return _.map(groupedFilters(), function(filterGroup) {
      return _.map(filterGroup, function(tag) {
        return filtersByTag[tag];
      });
    });
  });

  // Grouped tags of filters which are currently active.
  var activeFilters = self.disposedPureComputed(function() {
    return _.map(self.filters, function(filterGroup) {
      return _(filterGroup)
        .filter(function(f) { return f.active(); })
        .map("tag")
        .value();
    });
  });

  self.clearData = function() {
    self.forceVisibleIds([]);
    filtersByTag = {};
    _.invokeMap(subscriptions, "dispose");
    subscriptions = [];
  };

  function initFilter(filter) {
    if (!filtersByTag[filter.tag]) {
      var filterValue = ko.observable(filter["default"]);
      filtersByTag[filter.tag] = _.assing({}, filter, {active: filterValue});
      subscriptions.push(filterValue.subscribe(function() {
        self.forceVisibleIds([]);
      }));
    } else {
      filtersByTag[filter.tag].active(filter["default"]);
    }
    return filter.tag;
  }

  self.isFiltered = function(attachment) {
    var att = ko.unwrap(attachment);
    if (_.includes(forceVisibleIds, att.id)) {
      return true;
    }
    return service.isFiltered(attachment, activeFilters());
  };

  self.forceVisibility = function(attachmentId) {
    self.forceVisibleIds.push(attachmentId);
  };

  self.resetForcedVisibility = function() {
    self.forceVisibleIds([]);
  };

  self.setFilters = function(filters) {
    _.forEach(_.flatten(filters), initFilter);
    groupedFilters(_.map(filters, function(filterGroup) {
      return _.map(filterGroup, "tag");
    }));
  };

  self.getFilterValue = function(tag) {
    return _.get(filtersByTag, tag, "active");
  };

  var baseModelDispose = self.dispose;
  self.dispose = function() {
    self.clearData();
    baseModelDispose();
  };

  if (!_.isEmpty(filters)) {
    self.setFilters(filters);
  }
};
