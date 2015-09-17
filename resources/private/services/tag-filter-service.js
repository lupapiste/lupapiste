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

  var savedFilter = ko.pureComputed(function() {
    return util.getIn(applicationFiltersService.selected(), ["filter", "tags"]);
  });

  ko.computed(function() {
    self.selected([]);
    ko.utils.arrayPushAll(self.selected,
      _(self.data())
        .map("tags")
        .flatten()
        .filter(function(tag) {
          if (savedFilter()) {
            return  _.contains(savedFilter(), tag.id);
          } else {
            return _.contains(defaultFilter(), tag.id);
          }
        })
        .value());
  });
};
