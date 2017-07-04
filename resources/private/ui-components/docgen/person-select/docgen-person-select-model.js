LUPAPISTE.DocgenPersonSelectModel = function( params ) {
  "use strict";
  var self = this;

  var EMPTY = "empty";

  ko.utils.extend( self, new LUPAPISTE.DocgenInputModel( params ));

  // selectValue is a proxy for the underlying document data service
  // value. This approach allows for canceling.
  self.selectValue = ko.observable( self.value() );
  var partiesModel = _.get(params, "docModel.partiesModel");
  self.appId = partiesModel.applicationId;

  var collectionFn = _.get(params, "docModel.getCollection") || function() { return "documents" };
  var collection = collectionFn();

  self.personOptions = self.disposedComputed( function() {
    var itemsObservable = _.get(partiesModel, "personSelectorItems");
    return itemsObservable ? itemsObservable() : [];
  });

  self.optionsText = function( person ) {
    if (person.id) {
      return person.lastName + " " + person.firstName;
    }
  };

  self.setUserToDocumentOk = ko.observable(false);
  if (self.authModel.ok("set-user-to-document")) {
    var myNs = self.path.slice(0, self.path.length - 1).join(".");
    self.disposedSubscribe(self.selectValue, function(value) {
      if (value !== self.value()) {
        if (!_.isEmpty(value)) {
          ajax.command("set-user-to-document", { id: self.appId, documentId: self.documentId, userId: value, path: myNs, collection: collection })
            .success( _.partial( repository.load, self.appId, _.noop))
            .error(function(e) {
              if (e.text !== "error.application-does-not-have-given-auth") {
                error("Failed to set user to document", userId, self.docId, e);
              }
              notify.ajaxError(e);
            })
            .call();
        }
      }
    });
    self.setUserToDocumentOk(true);
  }

  /* // new invite
         if (authorizationModel.ok("invite-with-role")) {
           var button =
             $("<button>", {
               "class": "icon-remove positive",
               text: loc("personSelector.invite"),
               click: function () {
                 $("#invite-document-name").val(self.schemaName).change();
                 $("#invite-document-path").val(myNs).change();
                 $("#invite-document-id").val(self.docId).change();
                 LUPAPISTE.ModalDialog.open("#dialog-valtuutus");
                 return false;
               }
             });
           if (options && options.dataTestSpecifiers) {
             button.attr("data-test-id", "application-invite-" + self.schemaName);
           }
           button.appendTo(span);
         } */
  self.inviteWithRoleOk = ko.observable(false);
  if (self.authModel.ok("invite-with-role")) {
    self.inviteWithRoleOk(true);
  }

  self.invite = function() { alert(this); }

};