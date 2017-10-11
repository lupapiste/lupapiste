LUPAPISTE.EditableProclaimedBulletinModel = function(data, bulletin, auth) {
  "use strict";
  var self = this;

  var mapping = {
    copy: ["bulletinState"],
    proclamationStartsAt: {
      create: function(obj) {
        return ko.observable(new Date(obj.data));
      }
    },
    proclamationEndsAt: {
      create: function(obj) {
        return ko.observable(new Date(obj.data));
      }
    },
    proclamationText: {
      create: function(obj) {
        return ko.observable(obj.data);
      }
    }
  };

  ko.utils.extend(self, new LUPAPISTE.EditableBulletinModel(data, bulletin, auth, mapping));

  self.save = function() {
    self.sendEvent("publishBulletinService", "saveProclaimedBulletin", {
      bulletinId: bulletin().id,
      bulletinVersionId: data.id,
      proclamationEndsAt: self.proclamationEndsAt().getTime(),
      proclamationStartsAt: self.proclamationStartsAt().getTime(),
      proclamationText: self.proclamationText()
    });
  };
};
