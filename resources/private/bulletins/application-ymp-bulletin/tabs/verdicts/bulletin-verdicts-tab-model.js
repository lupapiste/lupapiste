LUPAPISTE.BulletinVerdictsTabModel = function(params) {
  "use strict";
  var self = this;

  self.verdicts = ko.observable();

  self.officialAt = ko.pureComputed(function() {
    return util.getIn(params, ["bulletin", "officialAt"]);
  });

  self.bulletinId = ko.pureComputed(function() {
    return util.getIn(params, ["bulletin", "application-id"]);
  });


  var verdicts = _.map(_.cloneDeep(params.verdicts() || []), function(verdict) {
    var paatokset = _.map(verdict.paatokset || [], function(paatos) {
      var poytakirjat = _.map(paatos.poytakirjat || [], function(pk) {
        var myAttachments = _.filter(params.attachments() || [], function(attachment) {
          var target = attachment.target;
          var idMatch = false;
          if (target && target.type === "verdict") {
            if (target.poytakirjaId) {
              idMatch = target.poytakirjaId === pk.id;
            } else if (target.urlHash) {
              idMatch = target.urlHash === pk.urlHash;
            } else {
              idMatch = target.id === verdict.id;
            }
          }
          return idMatch;
        }) || [];
        pk.attachments = myAttachments;
        return pk;
      });
      paatos.poytakirjat = poytakirjat;
      paatos.signatures = verdict.signatures;
      paatos.verdict = verdict;
      return paatos;
    });
    verdict.paatokset = paatokset;

    var pk = util.getIn(paatokset, [0, "poytakirjat", 0]) || {};
    var dates = util.getIn(paatokset, [0, "paivamaarat"]) || {};
    verdict.canBePublished = verdict.kuntalupatunnus && pk.status && pk.paatoksentekija && dates.anto && dates.lainvoimainen;

    return verdict;
  });

  self.verdicts(verdicts);
};
