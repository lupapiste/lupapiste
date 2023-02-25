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
    return _.map(self.filters(), function(filterGroup) {
      return _(filterGroup)
        .filter(function(filter) { return filter.active(); })
        .map("tag")
        .value();
    });
  });

  self.clearData = function() {
    forceVisibleIds([]);
    filtersByTag = {};
    _.invokeMap(subscriptions, "dispose");
    subscriptions = [];
  };

  function initFilter(filter) {
    if (!filtersByTag[filter.tag]) {
      var filterValue = ko.observable(filter["default"]);
      filtersByTag[filter.tag] = _.assign({}, filter, {active: filterValue});
      subscriptions.push(filterValue.subscribe(function() {
        forceVisibleIds([]);
      }));
    }
    return filter.tag;
  }

  self.isFiltered = function(attachment) {
    var att = ko.unwrap(attachment);
    if (_.includes(forceVisibleIds(), att.id)) {
      return true;
    }
    return service.isFiltered(activeFilters(), attachment);
  };

  self.apply = function(attachments) {
    return _.filter(attachments, self.isFiltered);
  };

  self.forceVisibility = function(attachmentId) {
    forceVisibleIds.push(attachmentId);
  };

  self.resetForcedVisibility = function() {
    forceVisibleIds([]);
  };

  self.setFilters = function(filters) {
    _.forEach(_.flatten(filters), initFilter);
    groupedFilters(_.map(filters, function(filterGroup) {
      return _.map(filterGroup, "tag");
    }));
  };

  self.getFilterValue = function(tag) {
    return _.get(filtersByTag, [tag, "active"]);
  };

  self.toggleAll = function(value) {
    var active = _.isBoolean(value) ? value : util.getIn(self.filters, [0, "active"]);
    _.map(_.flatten(self.filters()), function(filter) {
      filter.active(active);
    });
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
