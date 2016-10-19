LUPAPISTE.AttachmentsGroupSetModel = function(tagGroups) {
  "use strict";
  var self = this;
  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel());

  var groups = ko.observableArray();
  var groupsByPath = {};

  self.clearData = function() {
    _.forEach(groupsByPath, function(group) {
      group.subGroups([]);
    });
    groups([]);
    groupsByPath = {};
  };

  function getGroup(path, subGroups, defaultOpen) {
    var id = _.isArray(path) ? path.join(".") : path;
    if (!groupsByPath[id]) {
      groupsByPath[id] = { path: _.isString(path) ? path.split(".") : path,
                           accordionOpen: ko.observable(_.isNil(defaultOpen) ? true : defaultOpen),
                           subGroups: ko.observableArray(),
                           toggle: function(value) { toggle(this, value); },
                           toggleAll: function(value) { toggleAll(this, value); } };
    }
    if (subGroups) {
      groupsByPath[id].subGroups(subGroups);
    }
    return groupsByPath[id];
  }

  function toggle(group, value) {
    group.accordionOpen(_.isNil(value) ? !group.accordionOpen() : Boolean(value));
  }

  function toggleAll(group, value) {
    toggle(group, value);
    _.map(group.subGroups(), function(subGroup) { toggleAll(subGroup, value); });
  }

  self.toggleAll = function(value) {
    _.map(groups(), function(subGroup) { toggleAll(subGroup, value); });
  };

  function initTagGroups(path, tagGroups, defaultOpen) {
    return _.map(tagGroups, function(tagGroup) {
      var groupPath = path.push(_.head(tagGroup));
      var subGroups = initTagGroups(groupPath, _.tail(subGroups), defaultOpen);
      return getGroup(groupPath, subGroups, defaultOpen);
    });
  }

  self.setTagGroups = function(tagGroups, defaultOpen) {
    groups(initTagGroups([], tagGroups, defaultOpen));
  };

  self.getTagGroup = function(path) {
    if (_.isEmpty(path)) {
      return groups;
    } else {
      return getGroup[_.isArray(path) ? path.join(".") : path];
    }
  };

  var baseModelDispose = self.dispose;
  self.dispose = function() {
    self.clearData();
    baseModelDispose();
  };

  if (!_.isEmpty(tagGroups)) {
    self.setTagGroups(tagGroups);
  }
};
