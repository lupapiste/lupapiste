LUPAPISTE.EditableBulletinModel = function(data, bulletin, auth, mapping) {
  "use strict";
  var self = this;

  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel());

  mapping = !_.isEmpty(mapping) ? mapping : {
    copy: ["bulletinState"]
  };

  ko.mapping.fromJS(data, mapping, self);

  self.isValid = ko.observable(true);

  self.pending = ko.observable(false);

  self.edit = ko.observable(false);

  self.editable = ko.pureComputed(function() {
    return self.id() === _.last(bulletin().versions).id && auth;
  });

  self.save = _.noop;
};
