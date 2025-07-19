csharp
using (SqlConnection connection = new SqlConnection(connectionString))
{
    connection.Open();
    string userInput = Console.ReadLine();
    using (SqlCommand command = new SqlCommand("SELECT * FROM Users WHERE Username='" + userInput + "'", connection))
    {
        using (SqlDataReader reader = command.ExecuteReader())
        {
            while (reader.Read())
            {
                Console.WriteLine(reader["Username"].ToString());
            }
        }
    }
}