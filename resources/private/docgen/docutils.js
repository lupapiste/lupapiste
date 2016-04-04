var docutils = (function () {
  "use strict";

  // Magic key: if schema contains "_selected" radioGroup,
  // user can select only one of the schemas named in "_selected" group
  var SELECT_ONE_OF_GROUP_KEY = "_selected";

  function buildAccordionText(paths, data) {
    return _(paths)
      .map(function(path) {
        return ko.unwrap(_.get(data, path));
      })
      .reject(_.isEmpty)
      .value()
      .join(" ");
  }

  // resolve values from given paths
  function accordionText(paths, data) {
    if (_.isArray(paths)) { // set text only if the document has accordionPaths defined
      var firstPathValue = paths[0][0];
      // are we dealing with _selected special case
      var selectedValue = firstPathValue === SELECT_ONE_OF_GROUP_KEY ? _.get(data, firstPathValue)() : false;
      if (selectedValue) {
        var selectedPaths = _.filter(paths, function(path) { // filter paths according to _selected value
          return path[0] === selectedValue;
        });
        return buildAccordionText(selectedPaths, data);

      } else { // no _selected, use paths as is
        return buildAccordionText(paths, data);
      }
    }
  }

  function documentTitle(identifierText, operationText, accordionText) {
    var isEmpty = !identifierText && !operationText && !accordionText;
    if (!isEmpty) {
      var initStr = " - "; // not all are empty, so we separate description from titleLoc with initial '-'
      var identifierAndOperation = _.filter([identifierText, operationText]).join(": ");
      var withAccordionField = identifierAndOperation
                               ? _.filter([identifierAndOperation, accordionText]).join(" - ")
                               : accordionText;
      return initStr + withAccordionField;
    } else {
      return "";
    }
  }

  return {
    SELECT_ONE_OF_GROUP_KEY: SELECT_ONE_OF_GROUP_KEY,
    accordionText: accordionText,
    documentTitle: documentTitle
  };

})();
