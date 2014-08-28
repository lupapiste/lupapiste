var docgen = (function () {
  "use strict";

  function displayDocuments(containerSelector, application, documents, authorizationModel, options) {
    function updateOther(select) {
      var otherId = select.attr("data-select-other-id"),
          other = $("#" + otherId, select.parent().parent());
      other.parent().css("visibility", select.val() === "other" ? "visible" : "hidden");
    }
    function initSelectWithOther(i, e) { updateOther($(e)); }
    function selectWithOtherChanged() { updateOther($(this)); }

    function getDocumentOrder(doc) {
      var num = doc.schema.info.order || 7;
      return num * 10000000000 + doc.created / 1000;
    }

    var isDisabled = options && options.disabled;
    var sortedDocs = _.sortBy(documents, getDocumentOrder);
    var docgenDiv = $(containerSelector).empty();

    _.each(sortedDocs, function (doc) {
      var schema = doc.schema;
      var docModel = new DocModel(schema, doc, application, authorizationModel, options);

      docgenDiv.append(docModel.element);

      if (schema.info.repeating && !isDisabled && authorizationModel.ok('create-doc')) {

        var btn = $("<button>", {"id": schema.info.name + "_append_btn", "class": "btn block"})
          .text(loc(schema.info.name + "._append_label"))
          .click(function () {
            var self = this;
            ajax
              .command("create-doc", { schemaName: schema.info.name, id: application.id, collection: docModel.getCollection() })
              .success(function (data) {
                var newDoc = {
                  id: data.doc,
                  data: {},
                  meta: {},
                  validationErrors: doc.validationErrors
                };
                var newElem = new DocModel(schema, newDoc, application, authorizationModel).element;
                $(self).before(newElem);
              })
              .call();
          });

        docgenDiv.append(btn);
      }
    });

    $("select[data-select-other-id]", docgenDiv).each(initSelectWithOther).change(selectWithOtherChanged);
  }

  return {
    displayDocuments: displayDocuments
  };

})();
