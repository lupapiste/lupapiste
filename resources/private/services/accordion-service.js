//
// Provides services for accordion-toolbar component.
//
//
LUPAPISTE.AccordionService = function() {
  "use strict";
  var self = this;

  self.appModel = lupapisteApp.models.application;
  self.authModel = lupapisteApp.models.applicationAuthModel;

  self.documents = ko.pureComputed(function() {
    self.appModel.id(); // trigger when id changes
    var docsWithOperation = _.filter(self.appModel._js.documents, function(doc) {
      return doc["schema-info"].op;
    });
    return _.map(docsWithOperation, function(doc) {
      return {docId: doc.id, operation: doc["schema-info"].op, schema: doc.schema, data: doc.data};
    });
  });

  self.accordionFields = ko.observableArray([]);
  self.documents.subscribe(function() {
    var identifiers = _(self.documents())
      .map(function(doc) {
        var subSchema = _.find(doc.schema.body, "identifier"); // finds first
        if (subSchema) { // if we found the 'identifier' tag
          var key = _.get(subSchema, "name");
          return {docId: doc.docId, key: key, value: ko.observable(util.getIn(doc, ["data", key, "value"]))};
        }
      })
      .filter(_.identity) // cleanup undefineds
      .value();
    self.accordionFields(identifiers); // set the fields for each document
  });

  self.getIdentifier = function(docId) {
    return _.find(self.accordionFields(), {docId: docId});
  };

  hub.subscribe("accordionService::saveIdentifier", function(event) {
    var docId = event.docId;
    var value = event.value;
    var path = event.key;
    ajax
      .command("update-doc", {
          doc: docId,
          id: self.appModel.id.peek(),
          collection: "documents",
          updates: [[path, value]]
        }
      )
      .success(function(e) {
        util.showSavedIndicator(e);
      })
      .error(function (e) {
        util.showSavedIndicator(e);
      })
      .call();
  });

};
