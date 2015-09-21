LUPAPISTE.TagFilterService = function(tagsService) {
  "use strict";
  var self = this;

  self.data = tagsService.data;

  self.selected = ko.observableArray([]);

  var defaultFilter = ko.pureComputed(function() {
    var applicationFilters = _.first(lupapisteApp.models.currentUser.applicationFilters());
    return applicationFilters &&
           applicationFilters.filter.tags &&
           applicationFilters.filter.tags() ||
           [];
  });

  ko.computed(function() {
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
