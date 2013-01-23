package $name$.tests.integration

import org.scalatest.WordSpec
import org.scalatest.matchers.ShouldMatchers

class PlaceHolderSpec
  extends WordSpec
  with ShouldMatchers {

    "test" should {
      "always pass" in {
        true should be (true)
      }
    }
  }