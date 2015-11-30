LUPAPISTE.EditableBulletinModel = function(data, id) {
  "use strict";
  var self = this;

  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel());

  var mapping = {
    copy: ["bulletinState"],
  };

  ko.mapping.fromJS(data, mapping, self);

  self.isValid = ko.observable(true);

  self.pending = ko.observable(false);

  self.edit = ko.observable(false);

  self.editable = ko.observable(false);

  self.save = _.noop;
}
