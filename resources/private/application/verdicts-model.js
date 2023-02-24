LUPAPISTE.VerdictsModel = function() {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel() );

  self.verdicts = ko.observable();
  self.unfilteredVerdicts = ko.observableArray();
  self.applicationId = null;

  function poytakirjaAttachments( verdict, pk ) {
    return self.disposedComputed( function() {
      return _.filter(lupapisteApp.services.attachmentsService.attachments(),
                      function(attachment) {
                        var target = attachment().target;
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
                      });
    });
  }

  self.refresh = function(application) {
    self.applicationId = application.id;
    self.unfilteredVerdicts(application.verdicts);
    var verdicts = _(application.verdicts || [])
      .cloneDeep()
      .map(function(verdict) {
        var paatokset = _.map(verdict.paatokset || [], function(paatos) {
          var poytakirjat = _.map(paatos.poytakirjat || [], function(pk) {
            pk.attachments = poytakirjaAttachments( verdict, pk );
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
      })
      .filter(function(v) {return !_.isEmpty(v.paatokset);});

    self.verdicts(verdicts);
  };

};
