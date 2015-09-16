LUPAPISTE.TagFilterService = function(tagsService, applicationFiltersService) {
  "use strict";
  var self = this;

  self.data = tagsService.data;

  self.selected = ko.observableArray([]);

  var defaultFilter = ko.pureComputed(function() {
    var applicationFilters = _.find(applicationFiltersService.savedFilters(), function(f) {
      return f.isDefaultFilter();
    });
    return util.getIn(applicationFilters, ["filter", "tags"]) || [];
  });

  ko.computed(function() {
    self.selected([]);
    ko.utils.arrayPushAll(self.selected,
      _(self.data())
        .map("tags")
        .flatten()
        .filter(function(tag) {
          return _.contains(defaultFilter(), tag.id);
        })
        .value());
  });
};
