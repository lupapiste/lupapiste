LUPAPISTE.EditableVerdictGivenBulletinModel = function(data, bulletin, auth) {
  "use strict";
  var self = this;

  var mapping = {
    copy: ["bulletinState"],
    verdictGivenAt: {
      create: function(obj) {
        return ko.observable(new Date(obj.data));
      }
    },
    appealPeriodStartsAt: {
      create: function(obj) {
        return ko.observable(new Date(obj.data));
      }
    },
    appealPeriodEndsAt: {
      create: function(obj) {
        return ko.observable(new Date(obj.data));
      }
    },
    verdictGivenText: {
      create: function(obj) {
        return ko.observable(obj.data);
      }
    }
  };

  ko.utils.extend(self, new LUPAPISTE.EditableBulletinModel(data, bulletin, auth, mapping));

  self.save = function() {
    self.sendEvent("publishBulletinService", "saveVerdictGivenBulletin", {
      bulletinId: bulletin().id,
      bulletinVersionId: data.id,
      verdictGivenAt:       self.verdictGivenAt().getTime(),
      appealPeriodStartsAt: self.appealPeriodStartsAt().getTime(),
      appealPeriodEndsAt:   self.appealPeriodEndsAt().getTime(),
      verdictGivenText:     self.verdictGivenText() || ""
    });
  };
};
